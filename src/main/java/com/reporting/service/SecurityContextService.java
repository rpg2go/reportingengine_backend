package com.reporting.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Service to extract identity claims, user boundaries, and security restrictions 
 * from the active Spring Security JWT Context.
 */
@Service
public class SecurityContextService {

    private static final Logger log = LoggerFactory.getLogger(SecurityContextService.class);

    /**
     * Retrieves the active JWT token principal from the Security Context.
     *
     * @return the Jwt token, or null if the authentication is not a JWT token.
     */
    public Jwt getActiveJwt() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken jwtAuthenticationToken) {
            return jwtAuthenticationToken.getToken();
        } else if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt) {
            return jwt;
        }
        return null;
    }

    /**
     * Extracts the active user initials from the token claims.
     * Fallback to the subject name or "SYS" if unavailable.
     *
     * @return User initials string.
     */
    public String getCurrentUserInitials() {
        Jwt jwt = getActiveJwt();
        if (jwt != null) {
            String initials = jwt.getClaimAsString("user_initials");
            if (initials != null && !initials.isBlank()) {
                return initials;
            }
            // Fallback to name or email subject
            String sub = jwt.getSubject();
            if (sub != null && sub.contains("@")) {
                sub = sub.substring(0, sub.indexOf("@"));
            }
            return sub != null ? sub.toUpperCase() : "SYS";
        }
        return "SYS";
    }

    /**
     * Extracts the assigned country restrictions from the OIDC JWT token.
     *
     * @return List of country codes (e.g. ["DE", "RO"]). If empty, it means global access.
     */
    @SuppressWarnings("unchecked")
    public List<String> getCurrentUserCountryRestrictions() {
        Jwt jwt = getActiveJwt();
        if (jwt != null) {
            Object restrictions = jwt.getClaims().get("assigned_country_restrictions");
            if (restrictions instanceof List) {
                return (List<String>) restrictions;
            } else if (restrictions instanceof String str) {
                return List.of(str.split(","));
            }
        }
        return Collections.emptyList();
    }

    /**
     * Verifies if the authenticated user is authorized to query data for a specific country code.
     *
     * @param countryCode Target country code.
     * @return true if authorized, false otherwise.
     */
    public boolean isCurrentUserAuthorizedForCountry(String countryCode) {
        List<String> restrictions = getCurrentUserCountryRestrictions();
        if (restrictions.isEmpty()) {
            return true; // No restrictions implies global access
        }
        return restrictions.stream().anyMatch(rc -> rc.equalsIgnoreCase(countryCode));
    }

    /**
     * Injects country-level row-security filters into the incoming report configuration DTO.
     * Demonstrates how the extracted security claims are passed down to our query pipelines.
     *
     * @param sourceTable Table to filter (e.g., "analytics.fact_sales")
     * @return SQL filter clause representation based on user restrictions.
     */
    public String generateRowLevelSecurityClause(String sourceTable) {
        List<String> restrictions = getCurrentUserCountryRestrictions();
        if (restrictions.isEmpty()) {
            return "1=1"; // No restriction, return full access spine
        }

        // Example: map country restriction lists to a SQL filter string
        // Typically applied against "dim_countries.iso_code" or "country_code" columns.
        StringBuilder sql = new StringBuilder();
        sql.append("(");
        for (int i = 0; i < restrictions.size(); i++) {
            if (i > 0) {
                sql.append(" OR ");
            }
            sql.append("country_code = '").append(restrictions.get(i).replace("'", "''")).append("'");
        }
        sql.append(")");
        
        log.debug("Generated row-level security clause for user: {} against table: {}: {}", 
                getCurrentUserInitials(), sourceTable, sql);
        
        return sql.toString();
    }
}

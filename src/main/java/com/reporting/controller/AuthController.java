package com.reporting.controller;

import com.reporting.config.DevSecurityConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * Controller to handle authentication login checks and return OIDC-compliant mock JWT bearer tokens.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final DevSecurityConfig devSecurityConfig;

    @Value("${security.admin.username}")
    private String adminUsername;

    @Value("${security.admin.password}")
    private String adminPassword;

    @Value("${MOCK_FALLBACK_TOKEN:eyJhbGciOiJub25lIn0.eyJzdWIiOiJhZG1pbiIsInVzZXJfaW5pdGlhbHMiOiJBRCIsImFzc2lnbmVkX2NvdW50cnlfcmVzdHJpY3Rpb25zIjpbIkRFIiwiUk8iXSwicm9sZXMiOlsiVVNFUiJdfQ.}")
    private String fallbackToken;

    public AuthController(@Autowired(required = false) DevSecurityConfig devSecurityConfig) {
        this.devSecurityConfig = devSecurityConfig;
    }

    @GetMapping("/login")
    public ResponseEntity<?> login(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Basic ")) {
            return ResponseEntity.status(401).body(Map.of("message", "Unauthorized"));
        }

        try {
            String base64Credentials = authHeader.substring("Basic ".length()).trim();
            byte[] credDecoded = Base64.getDecoder().decode(base64Credentials);
            String credentials = new String(credDecoded, StandardCharsets.UTF_8);
            String[] values = credentials.split(":", 2);
            if (values.length == 2) {
                String username = values[0];
                String password = values[1];

                // Validate against configured admin/password properties
                if (adminUsername.equals(username) && adminPassword.equals(password)) {
                    String token = (devSecurityConfig != null) ? devSecurityConfig.getGeneratedToken() : fallbackToken;

                    Map<String, Object> response = new HashMap<>();
                    response.put("username", username);
                    response.put("authenticated", true);
                    response.put("token", token);
                    return ResponseEntity.ok(response);
                }
            }
        } catch (Exception e) {
            // Fall through
        }

        return ResponseEntity.status(401).body(Map.of("message", "Unauthorized"));
    }
}

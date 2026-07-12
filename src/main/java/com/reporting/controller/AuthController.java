package com.reporting.controller;

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

                // Simple check for local testing: validate against configured admin/password
                if ("admin".equals(username) && "password".equals(password)) {
                    // Generate a simulated JWT token string containing OIDC claims
                    String mockToken = "eyJhbGciOiJub25lIn0.eyJzdWIiOiJhZG1pbiIsInVzZXJfaW5pdGlhbHMiOiJBRCIsImFzc2lnbmVkX2NvdW50cnlfcmVzdHJpY3Rpb25zIjpbIkRFIiwiUk8iXSwicm9sZXMiOlsiVVNFUiJdfQ.";

                    Map<String, Object> response = new HashMap<>();
                    response.put("username", username);
                    response.put("authenticated", true);
                    response.put("token", mockToken);
                    return ResponseEntity.ok(response);
                }
            }
        } catch (Exception e) {
            // Fall through
        }

        return ResponseEntity.status(401).body(Map.of("message", "Unauthorized"));
    }
}

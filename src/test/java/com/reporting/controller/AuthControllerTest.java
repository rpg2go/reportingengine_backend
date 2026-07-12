package com.reporting.controller;

import com.reporting.config.SecurityConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = AuthController.class)
@Import(SecurityConfiguration.class)
@DisplayName("AuthController Unit Tests")
public class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("login without credentials should return 401 Unauthorized")
    public void login_withoutCredentials_shouldReturn401() throws Exception {
        mockMvc.perform(get("/api/auth/login"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("login with credentials should return 200 OK and credentials info")
    public void login_withCredentials_shouldReturn200AndUserInfo() throws Exception {
        String base64Credentials = java.util.Base64.getEncoder().encodeToString("admin:password".getBytes());
        mockMvc.perform(get("/api/auth/login")
                .header("Authorization", "Basic " + base64Credentials))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("admin"))
                .andExpect(jsonPath("$.authenticated").value(true))
                .andExpect(jsonPath("$.token").isNotEmpty());
    }
}

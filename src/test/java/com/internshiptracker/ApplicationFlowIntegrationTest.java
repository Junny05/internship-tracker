package com.internshiptracker;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class ApplicationFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String email;
    private String password;

    @BeforeEach
    void setUp() {
        email = "test-" + UUID.randomUUID() + "@example.com";
        password = "password123";
    }

    @Test
    void registerLoginCreateFetchUpdateStatusDelete() throws Exception {
        // Register
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s", "password": "%s"}
                                """.formatted(email, password)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").isNotEmpty());

        // Login
        MvcResult loginResult = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s", "password": "%s"}
                                """.formatted(email, password)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andReturn();

        JsonNode loginJson = objectMapper.readTree(loginResult.getResponse().getContentAsString());
        String token = loginJson.get("token").asText();
        String authHeader = "Bearer " + token;

        // Applications require auth
        mockMvc.perform(get("/applications"))
                .andExpect(status().isUnauthorized());

        // Create application
        MvcResult createResult = mockMvc.perform(post("/applications")
                        .header("Authorization", authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "company": "Acme Corp",
                                  "role": "Backend Intern",
                                  "status": "APPLIED",
                                  "appliedDate": "2026-06-01",
                                  "notes": "Referred by a friend",
                                  "link": "https://acme.example.com/careers/123"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.company").value("Acme Corp"))
                .andExpect(jsonPath("$.status").value("APPLIED"))
                .andReturn();

        JsonNode created = objectMapper.readTree(createResult.getResponse().getContentAsString());
        String applicationId = created.get("id").asText();

        // Fetch it
        mockMvc.perform(get("/applications/{id}", applicationId)
                        .header("Authorization", authHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.company").value("Acme Corp"));

        // List with status filter
        mockMvc.perform(get("/applications").param("status", "APPLIED")
                        .header("Authorization", authHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(applicationId));

        // Update status only
        mockMvc.perform(patch("/applications/{id}/status", applicationId)
                        .header("Authorization", authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status": "INTERVIEW"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INTERVIEW"));

        // Delete it
        mockMvc.perform(delete("/applications/{id}", applicationId)
                        .header("Authorization", authHeader))
                .andExpect(status().isNoContent());

        // Now gone - 404, not leaked as 403
        mockMvc.perform(get("/applications/{id}", applicationId)
                        .header("Authorization", authHeader))
                .andExpect(status().isNotFound());
    }

    @Test
    void cannotAccessAnotherUsersApplication() throws Exception {
        String otherEmail = "other-" + UUID.randomUUID() + "@example.com";

        String tokenA = registerAndLogin(email, password);
        String tokenB = registerAndLogin(otherEmail, password);

        MvcResult createResult = mockMvc.perform(post("/applications")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"company": "Acme", "role": "Intern", "status": "WISHLIST"}
                                """))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode created = objectMapper.readTree(createResult.getResponse().getContentAsString());
        String applicationId = created.get("id").asText();

        mockMvc.perform(get("/applications/{id}", applicationId)
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isNotFound());
    }

    private String registerAndLogin(String email, String password) throws Exception {
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s", "password": "%s"}
                                """.formatted(email, password)))
                .andExpect(status().isCreated());

        MvcResult result = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s", "password": "%s"}
                                """.formatted(email, password)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        return json.get("token").asText();
    }
}

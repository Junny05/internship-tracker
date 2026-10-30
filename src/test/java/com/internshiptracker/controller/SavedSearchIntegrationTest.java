package com.internshiptracker.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.internshiptracker.entity.SeenListing;
import com.internshiptracker.repository.SavedSearchRepository;
import com.internshiptracker.repository.SeenListingRepository;
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
class SavedSearchIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private SavedSearchRepository savedSearchRepository;

    @Autowired
    private SeenListingRepository seenListingRepository;

    @Test
    void searchesRequireAuthentication() throws Exception {
        mockMvc.perform(get("/searches"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createListAndDeleteAreScopedPerUser() throws Exception {
        String ownerToken = registerAndGetToken();
        String otherToken = registerAndGetToken();

        // Owner creates a saved search
        MvcResult created = mockMvc.perform(post("/searches")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"keywords": "java intern", "location": "Austin"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.keywords").value("java intern"))
                .andExpect(jsonPath("$.location").value("Austin"))
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andReturn();
        String searchId = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asText();

        // Owner sees it in their list
        mockMvc.perform(get("/searches")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(searchId));

        // Another user does not see it
        mockMvc.perform(get("/searches")
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        // Another user cannot delete it: 404, same as a missing row
        mockMvc.perform(delete("/searches/" + searchId)
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isNotFound());

        // Owner deletes it, and a second delete is 404
        mockMvc.perform(delete("/searches/" + searchId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(delete("/searches/" + searchId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/searches")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void deletingASearchWithMatchedListingsDoesNotViolateForeignKey() throws Exception {
        String token = registerAndGetToken();

        MvcResult created = mockMvc.perform(post("/searches")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"keywords": "intern", "location": "Austin"}
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        String searchId = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asText();

        // Simulate the poller having already found and recorded a matching listing
        seenListingRepository.save(SeenListing.builder()
                .savedSearch(savedSearchRepository.findById(UUID.fromString(searchId)).orElseThrow())
                .externalId("9001")
                .build());

        mockMvc.perform(delete("/searches/" + searchId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
    }

    @Test
    void deleteUnknownIdReturnsNotFound() throws Exception {
        String token = registerAndGetToken();

        mockMvc.perform(delete("/searches/" + UUID.randomUUID())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void createRejectsBlankKeywords() throws Exception {
        String token = registerAndGetToken();

        mockMvc.perform(post("/searches")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"keywords": "   ", "location": "Austin"}
                                """))
                .andExpect(status().isBadRequest());
    }

    private String registerAndGetToken() throws Exception {
        String email = "search-" + UUID.randomUUID() + "@example.com";
        MvcResult result = mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s", "password": "password123"}
                                """.formatted(email)))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        return json.get("token").asText();
    }
}

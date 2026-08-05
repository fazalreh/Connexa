package com.connexa.api.api.v1;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class ProtectedFlowControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void protectedAccountEndpointsRejectClientSuppliedIdentityHeaders() throws Exception {
        mockMvc.perform(get("/api/v1/me")
                        .header("X-Identity", "unverified-client-value"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
                .andExpect(jsonPath("$.requestId").isNotEmpty())
                .andExpect(jsonPath("$.type").value("https://api.connexa.local/problems/authentication-required"));
    }

    @Test
    void protectedStateAndAssistantEndpointsFailClosedWithoutVerifiedIdentity() throws Exception {
        UUID eventId = UUID.randomUUID();

        mockMvc.perform(put("/api/v1/events/{eventId}/saved", eventId))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/assistant/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"What events are available?\"}"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/notifications"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/organizer/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Community meetup",
                                  "description": "A planned community event.",
                                  "location": "Community hall",
                                  "startsAt": "2026-08-05T10:00:00Z",
                                  "endsAt": "2026-08-05T11:00:00Z",
                                  "timeZone": "Asia/Karachi",
                                  "category": "Community",
                                  "capacity": 80
                                }
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void protectedRoutesAuthenticateBeforeRequestBodyValidation() throws Exception {
        mockMvc.perform(post("/api/v1/assistant/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }
}

package com.connexa.api.api.v1;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class EventControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void returnsAnEmptyContractFirstEventPage() throws Exception {
        mockMvc.perform(get("/api/v1/events"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items").isEmpty())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.total").value(0));
    }

    @Test
    void rejectsAnInvalidTimeRangeWithAProblemResponse() throws Exception {
        mockMvc.perform(get("/api/v1/events")
                        .param("from", "2026-08-05T12:00:00Z")
                        .param("to", "2026-08-05T10:00:00Z"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("https://api.connexa.local/problems/invalid-request"))
                .andExpect(jsonPath("$.requestId").exists());
    }

    @Test
    void returnsATraceableProblemWhenAnEventDoesNotExist() throws Exception {
        mockMvc.perform(get("/api/v1/events/{eventId}", UUID.randomUUID())
                        .header(RequestIdFilter.HEADER_NAME, "connexa-not-found-001"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("https://api.connexa.local/problems/event-not-found"))
                .andExpect(jsonPath("$.requestId").value("connexa-not-found-001"));
    }
}

package com.connexa.api.api.v1;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class PlatformStatusControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void returnsSafePlatformStatusAndPreservesValidRequestId() throws Exception {
        mockMvc.perform(get("/api/v1/platform/status")
                        .header(RequestIdFilter.HEADER_NAME, "connexa-foundation-001"))
                .andExpect(status().isOk())
                .andExpect(header().string(RequestIdFilter.HEADER_NAME, "connexa-foundation-001"))
                .andExpect(jsonPath("$.service").value("connexa-api"))
                .andExpect(jsonPath("$.status").value("available"))
                .andExpect(jsonPath("$.requestId").value("connexa-foundation-001"))
                .andExpect(jsonPath("$.disabledIntegrations[0]").value("firebase"));
    }
}

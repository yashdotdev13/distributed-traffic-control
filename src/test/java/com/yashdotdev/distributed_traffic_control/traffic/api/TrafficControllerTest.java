package com.yashdotdev.distributed_traffic_control.traffic.api;

import com.yashdotdev.distributed_traffic_control.traffic.TrafficControlService;
import com.yashdotdev.distributed_traffic_control.traffic.TrafficDecision;
import com.yashdotdev.distributed_traffic_control.traffic.TrafficDecisionStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(TrafficController.class)
class TrafficControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TrafficControlService trafficControlService;

    @Test
    void shouldReturnAllowedResponseForValidRequest()
            throws Exception {

        TrafficDecision decision =
                new TrafficDecision(
                        TrafficDecisionStatus.ALLOWED,
                        "Request allowed",
                        99
                );

        when(trafficControlService.evaluate(any()))
                .thenReturn(decision);

        mockMvc.perform(
                        post("/api/v1/traffic/evaluate")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "requestId": "req-001",
                                          "subject": {
                                            "type": "USER",
                                            "subjectId": "user-123"
                                          },
                                          "resource": "/api/orders",
                                          "requestedAt": "2026-09-03T02:00:00Z"
                                        }
                                        """)
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.status")
                                .value("ALLOWED")
                )
                .andExpect(
                        jsonPath("$.reason")
                                .value("Request allowed")
                )
                .andExpect(
                        jsonPath("$.remainingCapacity")
                                .value(99)
                );

        verify(trafficControlService)
                .evaluate(any());
    }

    @Test
    void shouldReturnRejectedResponseWhenTrafficIsRejected()
            throws Exception {

        TrafficDecision decision =
                new TrafficDecision(
                        TrafficDecisionStatus.REJECTED,
                        "Traffic quota exhausted",
                        0
                );

        when(trafficControlService.evaluate(any()))
                .thenReturn(decision);

        mockMvc.perform(
                        post("/api/v1/traffic/evaluate")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "requestId": "req-002",
                                          "subject": {
                                            "type": "USER",
                                            "subjectId": "user-123"
                                          },
                                          "resource": "/api/orders",
                                          "requestedAt": "2026-09-03T02:00:00Z"
                                        }
                                        """)
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.status")
                                .value("REJECTED")
                )
                .andExpect(
                        jsonPath("$.reason")
                                .value("Traffic quota exhausted")
                )
                .andExpect(
                        jsonPath("$.remainingCapacity")
                                .value(0)
                );

        verify(trafficControlService)
                .evaluate(any());
    }

    @Test
    void shouldReturnBadRequestForInvalidRequest()
            throws Exception {

        mockMvc.perform(
                        post("/api/v1/traffic/evaluate")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "requestId": "",
                                          "subject": {
                                            "type": "USER",
                                            "subjectId": ""
                                          },
                                          "resource": "",
                                          "requestedAt": null
                                        }
                                        """)
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(
                trafficControlService
        );
    }
}
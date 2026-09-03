package com.yashdotdev.distributed_traffic_control.policy.api;

import com.yashdotdev.distributed_traffic_control.policy.PolicyManagementService;
import com.yashdotdev.distributed_traffic_control.policy.PolicyStatus;
import com.yashdotdev.distributed_traffic_control.policy.TrafficPolicy;
import com.yashdotdev.distributed_traffic_control.policy.TrafficPolicyType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PolicyController.class)
class PolicyControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PolicyManagementService policyManagementService;

    @Test
    void shouldCreatePolicy() throws Exception {

        TrafficPolicy policy = new TrafficPolicy(
                "orders-policy",
                "Orders Policy",
                TrafficPolicyType.TOKEN_BUCKET,
                PolicyStatus.ACTIVE,
                100,
                10,
                null,
                Instant.parse("2026-09-04T00:00:00Z")
        );

        when(policyManagementService.registerPolicy(
                eq("/api/orders"),
                any(TrafficPolicy.class)
        )).thenReturn(policy);

        mockMvc.perform(
                        post("/api/v1/policies")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "policyId": "orders-policy",
                                          "name": "Orders Policy",
                                          "type": "TOKEN_BUCKET",
                                          "status": "ACTIVE",
                                          "capacity": 100,
                                          "refillRate": 10,
                                          "windowDuration": null,
                                          "createdAt": "2026-09-04T00:00:00Z",
                                          "resource": "/api/orders"
                                        }
                                        """)
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.policyId")
                                .value("orders-policy")
                )
                .andExpect(
                        jsonPath("$.name")
                                .value("Orders Policy")
                )
                .andExpect(
                        jsonPath("$.type")
                                .value("TOKEN_BUCKET")
                )
                .andExpect(
                        jsonPath("$.status")
                                .value("ACTIVE")
                )
                .andExpect(
                        jsonPath("$.capacity")
                                .value(100)
                )
                .andExpect(
                        jsonPath("$.refillRate")
                                .value(10)
                )
                .andExpect(
                        jsonPath("$.resource")
                                .value("/api/orders")
                );

        verify(policyManagementService)
                .registerPolicy(
                        eq("/api/orders"),
                        any(TrafficPolicy.class)
                );
    }

    @Test
    void shouldCreateFixedWindowPolicy() throws Exception {

        TrafficPolicy policy = new TrafficPolicy(
                "orders-fixed-window",
                "Orders Fixed Window Policy",
                TrafficPolicyType.FIXED_WINDOW,
                PolicyStatus.ACTIVE,
                10,
                1,
                Duration.ofMinutes(1),
                Instant.parse("2026-09-04T00:00:00Z")
        );

        when(policyManagementService.registerPolicy(
                eq("/api/orders"),
                any(TrafficPolicy.class)
        )).thenReturn(policy);

        mockMvc.perform(
                        post("/api/v1/policies")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "policyId": "orders-fixed-window",
                                          "name": "Orders Fixed Window Policy",
                                          "type": "FIXED_WINDOW",
                                          "status": "ACTIVE",
                                          "capacity": 10,
                                          "refillRate": 1,
                                          "windowDuration": "PT1M",
                                          "createdAt": "2026-09-04T00:00:00Z",
                                          "resource": "/api/orders"
                                        }
                                        """)
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.policyId")
                                .value("orders-fixed-window")
                )
                .andExpect(
                        jsonPath("$.type")
                                .value("FIXED_WINDOW")
                )
                .andExpect(
                        jsonPath("$.windowDuration")
                                .value("PT1M")
                );

        verify(policyManagementService)
                .registerPolicy(
                        eq("/api/orders"),
                        any(TrafficPolicy.class)
                );
    }

    @Test
    void shouldDeletePolicy() throws Exception {

        mockMvc.perform(
                        delete("/api/v1/policies")
                                .param(
                                        "resource",
                                        "/api/orders"
                                )
                )
                .andExpect(status().isNoContent());

        verify(policyManagementService)
                .removePolicy("/api/orders");
    }

    @Test
    void shouldReturnBadRequestForInvalidPolicyRequest()
            throws Exception {

        mockMvc.perform(
                        post("/api/v1/policies")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "policyId": "",
                                          "name": "",
                                          "type": null,
                                          "status": null,
                                          "capacity": 0,
                                          "refillRate": 0,
                                          "windowDuration": null,
                                          "createdAt": null,
                                          "resource": ""
                                        }
                                        """)
                )
                .andExpect(status().isBadRequest());
    }
}
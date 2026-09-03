package com.yashdotdev.distributed_traffic_control.policy.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@AutoConfigureTestRestTemplate
class PolicyControllerIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void shouldCreatePolicyThroughRealHttpEndpoint()
            throws Exception {

        String url =
                "http://localhost:"
                        + port
                        + "/api/v1/policies";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(
                MediaType.APPLICATION_JSON
        );

        HttpEntity<String> request =
                new HttpEntity<>(
                        """
                        {
                          "policyId": "orders-policy",
                          "name": "Orders Policy",
                          "type": "TOKEN_BUCKET",
                          "status": "ACTIVE",
                          "capacity": 5,
                          "refillRate": 1,
                          "windowDuration": null,
                          "createdAt": "2026-09-04T00:00:00Z",
                          "resource": "/api/policy-test/orders"
                        }
                        """,
                        headers
                );

        ResponseEntity<PolicyResponse> response =
                restTemplate.exchange(
                        url,
                        HttpMethod.POST,
                        request,
                        PolicyResponse.class
                );

        assertEquals(
                HttpStatus.OK,
                response.getStatusCode()
        );

        assertNotNull(response.getBody());

        assertEquals(
                "orders-policy",
                response.getBody().getPolicyId()
        );

        assertEquals(
                "Orders Policy",
                response.getBody().getName()
        );

        assertEquals(
                "TOKEN_BUCKET",
                response.getBody().getType().name()
        );

        assertEquals(
                "ACTIVE",
                response.getBody().getStatus().name()
        );

        assertEquals(
                5,
                response.getBody().getCapacity()
        );

        assertEquals(
                1,
                response.getBody().getRefillRate()
        );

        assertEquals(
                "/api/policy-test/orders",
                response.getBody().getResource()
        );
    }

    @Test
    void shouldDeletePolicyThroughRealHttpEndpoint()
            throws Exception {

        String createUrl =
                "http://localhost:"
                        + port
                        + "/api/v1/policies";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(
                MediaType.APPLICATION_JSON
        );

        HttpEntity<String> createRequest =
                new HttpEntity<>(
                        """
                        {
                          "policyId": "delete-test-policy",
                          "name": "Delete Test Policy",
                          "type": "TOKEN_BUCKET",
                          "status": "ACTIVE",
                          "capacity": 5,
                          "refillRate": 1,
                          "windowDuration": null,
                          "createdAt": "%s",
                          "resource": "/api/delete-test"
                        }
                        """.formatted(Instant.now()),
                        headers
                );

        ResponseEntity<PolicyResponse> createResponse =
                restTemplate.exchange(
                        createUrl,
                        HttpMethod.POST,
                        createRequest,
                        PolicyResponse.class
                );

        assertEquals(
                HttpStatus.OK,
                createResponse.getStatusCode()
        );

        String deleteUrl =
                createUrl
                        + "?resource="
                        + "/api/delete-test";

        ResponseEntity<Void> deleteResponse =
                restTemplate.exchange(
                        deleteUrl,
                        HttpMethod.DELETE,
                        HttpEntity.EMPTY,
                        Void.class
                );

        assertEquals(
                HttpStatus.NO_CONTENT,
                deleteResponse.getStatusCode()
        );
    }

    @Test
    void shouldRejectInvalidPolicyThroughRealHttpEndpoint()
            throws Exception {

        String url =
                "http://localhost:"
                        + port
                        + "/api/v1/policies";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(
                MediaType.APPLICATION_JSON
        );

        HttpEntity<String> request =
                new HttpEntity<>(
                        """
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
                        """,
                        headers
                );

        ResponseEntity<String> response =
                restTemplate.exchange(
                        url,
                        HttpMethod.POST,
                        request,
                        String.class
                );

        assertEquals(
                HttpStatus.BAD_REQUEST,
                response.getStatusCode()
        );
    }
}
package com.yashdotdev.distributed_traffic_control.policy.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class PolicyControllerIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void shouldCreatePolicyThroughRealHttpEndpoint() throws Exception {

        String url = "http://localhost:" + port + "/api/v1/policies";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<String> request = new HttpEntity<>("""
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
                """, headers);

        ResponseEntity<PolicyResponse> response = restTemplate.exchange(url, HttpMethod.POST, request, PolicyResponse.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("orders-policy", response.getBody().getPolicyId());
        assertEquals("Orders Policy", response.getBody().getName());
        assertEquals("TOKEN_BUCKET", response.getBody().getType().name());
        assertEquals("ACTIVE", response.getBody().getStatus().name());
        assertEquals(5, response.getBody().getCapacity());
        assertEquals(1, response.getBody().getRefillRate());
        assertEquals("/api/policy-test/orders", response.getBody().getResource());
    }

    @Test
    void shouldDeletePolicyThroughRealHttpEndpoint() throws Exception {

        String createUrl = "http://localhost:" + port + "/api/v1/policies";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<String> createRequest = new HttpEntity<>("""
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
                """.formatted(Instant.now()), headers);

        ResponseEntity<PolicyResponse> createResponse = restTemplate.exchange(createUrl, HttpMethod.POST, createRequest, PolicyResponse.class);
        assertEquals(HttpStatus.OK, createResponse.getStatusCode());
        String deleteUrl = createUrl + "?resource=" + "/api/delete-test";
        ResponseEntity<Void> deleteResponse = restTemplate.exchange(deleteUrl, HttpMethod.DELETE, HttpEntity.EMPTY, Void.class);
        assertEquals(HttpStatus.NO_CONTENT, deleteResponse.getStatusCode());
    }

    @Test
    void shouldRejectInvalidPolicyThroughRealHttpEndpoint() throws Exception {

        String url = "http://localhost:" + port + "/api/v1/policies";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<String> request = new HttpEntity<>("""
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
                """, headers);
        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, request, String.class);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void shouldListPoliciesThroughRealHttpEndpoint() {
        String resource = "/api/contract/list";

        restTemplate.exchange(
                baseUrl(),
                HttpMethod.POST,
                createRequest("list-policy", "List Policy", resource, 10),
                PolicyResponse.class
        );

        ResponseEntity<List<PolicyResponse>> response =
                restTemplate.exchange(
                        baseUrl(),
                        HttpMethod.GET,
                        null,
                        new ParameterizedTypeReference<>() {}
                );

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().stream()
                .anyMatch(policy -> resource.equals(policy.getResource())));
    }

    @Test
    void shouldRetrievePolicyByResourceThroughRealHttpEndpoint() {
        String resource = "/api/contract/read";

        restTemplate.exchange(
                baseUrl(),
                HttpMethod.POST,
                createRequest("read-policy", "Read Policy", resource, 20),
                PolicyResponse.class
        );

        ResponseEntity<PolicyResponse> response =
                restTemplate.getForEntity(
                        policyUri(resource),
                        PolicyResponse.class
                );

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("read-policy", response.getBody().getPolicyId());
        assertEquals(resource, response.getBody().getResource());
        assertEquals(20, response.getBody().getCapacity());
    }

    @Test
    void shouldReturnNotFoundForMissingPolicy() {
        ResponseEntity<String> response =
                restTemplate.getForEntity(
                        policyUri("/api/contract/missing"),
                        String.class
                );

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    void shouldUpdatePolicyThroughRealHttpEndpoint() {
        String resource = "/api/contract/update";

        restTemplate.exchange(
                baseUrl(),
                HttpMethod.POST,
                createRequest("update-policy", "Original Policy", resource, 10),
                PolicyResponse.class
        );

        ResponseEntity<PolicyResponse> response =
                restTemplate.exchange(
                        policyUri(resource),
                        HttpMethod.PUT,
                        createRequest("update-policy", "Updated Policy", resource, 50),
                        PolicyResponse.class
                );

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Updated Policy", response.getBody().getName());
        assertEquals(50, response.getBody().getCapacity());
        assertEquals(resource, response.getBody().getResource());
    }

    @Test
    void shouldReturnNotFoundAfterDeletingPolicy() {
        String resource = "/api/contract/delete";

        restTemplate.exchange(
                baseUrl(),
                HttpMethod.POST,
                createRequest("delete-policy-contract", "Delete Policy", resource, 10),
                PolicyResponse.class
        );

        ResponseEntity<Void> deleteResponse =
                restTemplate.exchange(
                        policyUri(resource),
                        HttpMethod.DELETE,
                        HttpEntity.EMPTY,
                        Void.class
                );

        assertEquals(HttpStatus.NO_CONTENT, deleteResponse.getStatusCode());

        ResponseEntity<String> getResponse =
                restTemplate.getForEntity(
                        policyUri(resource),
                        String.class
                );

        assertEquals(HttpStatus.NOT_FOUND, getResponse.getStatusCode());
    }

    private String baseUrl() {
        return "http://localhost:" + port + "/api/v1/policies";
    }

    private java.net.URI policyUri(String resource) {
        return UriComponentsBuilder.fromUriString(baseUrl())
                .queryParam("resource", resource)
                .build()
                .encode()
                .toUri();
    }

    private HttpEntity<String> createRequest(String policyId, String name, String resource, long capacity) {

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        String body = """
                {
                  "policyId": "%s",
                  "name": "%s",
                  "type": "TOKEN_BUCKET",
                  "status": "ACTIVE",
                  "capacity": %d,
                  "refillRate": 1,
                  "windowDuration": null,
                  "createdAt": "2026-09-04T00:00:00Z",
                  "resource": "%s"
                }
                """.formatted(policyId, name, capacity, resource);

        return new HttpEntity<>(body, headers);
    }
}
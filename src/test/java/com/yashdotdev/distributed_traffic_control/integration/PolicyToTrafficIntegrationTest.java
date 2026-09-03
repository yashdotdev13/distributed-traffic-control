package com.yashdotdev.distributed_traffic_control.integration;

import com.yashdotdev.distributed_traffic_control.policy.api.PolicyResponse;
import com.yashdotdev.distributed_traffic_control.traffic.TrafficDecisionStatus;
import com.yashdotdev.distributed_traffic_control.traffic.api.TrafficEvaluationResponse;
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
class PolicyToTrafficIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void shouldUseHttpCreatedPolicyDuringTrafficEvaluation() {

        String policyUrl =
                "http://localhost:"
                        + port
                        + "/api/v1/policies";

        String trafficUrl =
                "http://localhost:"
                        + port
                        + "/api/v1/traffic/evaluate";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(
                MediaType.APPLICATION_JSON
        );

        String resource =
                "/api/integration/orders";

        HttpEntity<String> policyRequest =
                new HttpEntity<>(
                        """
                        {
                          "policyId": "integration-orders-policy",
                          "name": "Integration Orders Policy",
                          "type": "FIXED_WINDOW",
                          "status": "ACTIVE",
                          "capacity": 2,
                          "refillRate": 1,
                          "windowDuration": "PT1M",
                          "createdAt": "2026-09-04T00:00:00Z",
                          "resource": "%s"
                        }
                        """.formatted(resource),
                        headers
                );

        ResponseEntity<PolicyResponse> policyResponse =
                restTemplate.exchange(
                        policyUrl,
                        HttpMethod.POST,
                        policyRequest,
                        PolicyResponse.class
                );

        assertEquals(
                HttpStatus.OK,
                policyResponse.getStatusCode()
        );

        assertNotNull(policyResponse.getBody());

        assertEquals(
                "integration-orders-policy",
                policyResponse.getBody().getPolicyId()
        );

        assertEquals(
                resource,
                policyResponse.getBody().getResource()
        );

        String firstRequest =
                """
                {
                  "requestId": "integration-request-1",
                  "subject": {
                    "type": "USER",
                    "subjectId": "integration-user"
                  },
                  "resource": "%s",
                  "requestedAt": "%s"
                }
                """.formatted(
                        resource,
                        Instant.now()
                );

        ResponseEntity<TrafficEvaluationResponse> firstResponse =
                evaluateTraffic(
                        trafficUrl,
                        headers,
                        firstRequest
                );

        assertEquals(
                HttpStatus.OK,
                firstResponse.getStatusCode()
        );

        assertNotNull(firstResponse.getBody());

        assertEquals(
                TrafficDecisionStatus.ALLOWED,
                firstResponse.getBody().getStatus()
        );

        assertEquals(
                1,
                firstResponse.getBody().getRemainingCapacity()
        );

        String secondRequest =
                """
                {
                  "requestId": "integration-request-2",
                  "subject": {
                    "type": "USER",
                    "subjectId": "integration-user"
                  },
                  "resource": "%s",
                  "requestedAt": "%s"
                }
                """.formatted(
                        resource,
                        Instant.now()
                );

        ResponseEntity<TrafficEvaluationResponse> secondResponse =
                evaluateTraffic(
                        trafficUrl,
                        headers,
                        secondRequest
                );

        assertEquals(
                HttpStatus.OK,
                secondResponse.getStatusCode()
        );

        assertNotNull(secondResponse.getBody());

        assertEquals(
                TrafficDecisionStatus.ALLOWED,
                secondResponse.getBody().getStatus()
        );

        assertEquals(
                0,
                secondResponse.getBody().getRemainingCapacity()
        );

        String thirdRequest =
                """
                {
                  "requestId": "integration-request-3",
                  "subject": {
                    "type": "USER",
                    "subjectId": "integration-user"
                  },
                  "resource": "%s",
                  "requestedAt": "%s"
                }
                """.formatted(
                        resource,
                        Instant.now()
                );

        ResponseEntity<TrafficEvaluationResponse> thirdResponse =
                evaluateTraffic(
                        trafficUrl,
                        headers,
                        thirdRequest
                );

        assertEquals(
                HttpStatus.OK,
                thirdResponse.getStatusCode()
        );

        assertNotNull(thirdResponse.getBody());

        assertEquals(
                TrafficDecisionStatus.REJECTED,
                thirdResponse.getBody().getStatus()
        );

        assertEquals(
                0,
                thirdResponse.getBody().getRemainingCapacity()
        );
    }

    private ResponseEntity<TrafficEvaluationResponse> evaluateTraffic(
            String url,
            HttpHeaders headers,
            String body
    ) {

        HttpEntity<String> request =
                new HttpEntity<>(
                        body,
                        headers
                );

        return restTemplate.exchange(
                url,
                HttpMethod.POST,
                request,
                TrafficEvaluationResponse.class
        );
    }
}
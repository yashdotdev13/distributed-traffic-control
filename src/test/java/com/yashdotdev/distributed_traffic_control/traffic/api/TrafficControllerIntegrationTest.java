package com.yashdotdev.distributed_traffic_control.traffic.api;


import com.yashdotdev.distributed_traffic_control.traffic.TrafficDecisionStatus;
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
class TrafficControllerIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void shouldEvaluateTrafficThroughRealHttpEndpoint() {

        String url =
                "http://localhost:"
                        + port
                        + "/api/v1/traffic/evaluate";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<String> request =
                new HttpEntity<>(
                        """
                        {
                          "requestId": "req-e2e-001",
                          "subject": {
                            "type": "USER",
                            "subjectId": "e2e-user"
                          },
                          "resource": "/api/orders",
                          "requestedAt": "%s"
                        }
                        """.formatted(Instant.now()),
                        headers
                );

        ResponseEntity<TrafficEvaluationResponse> response =
                restTemplate.exchange(
                        url,
                        HttpMethod.POST,
                        request,
                        TrafficEvaluationResponse.class
                );

        assertEquals(
                HttpStatus.OK,
                response.getStatusCode()
        );

        assertNotNull(response.getBody());

        assertEquals(
                TrafficDecisionStatus.ALLOWED,
                response.getBody().getStatus()
        );

        assertEquals(
                "Request allowed",
                response.getBody().getReason()
        );

        assertEquals(
                99,
                response.getBody().getRemainingCapacity()
        );
    }

    @Test
    void shouldReturnBadRequestForInvalidHttpRequest() {

        String url =
                "http://localhost:"
                        + port
                        + "/api/v1/traffic/evaluate";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<String> request =
                new HttpEntity<>(
                        """
                        {
                          "requestId": "",
                          "subject": {
                            "type": "USER",
                            "subjectId": ""
                          },
                          "resource": "",
                          "requestedAt": null
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
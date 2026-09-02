package com.yashdotdev.distributed_traffic_control.traffic.api;

import com.yashdotdev.distributed_traffic_control.traffic.TrafficControlService;
import com.yashdotdev.distributed_traffic_control.traffic.TrafficDecision;
import com.yashdotdev.distributed_traffic_control.traffic.TrafficRequest;
import com.yashdotdev.distributed_traffic_control.traffic.TrafficSubject;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/traffic")
@RequiredArgsConstructor
public class TrafficController {

    private final TrafficControlService trafficControlService;

    @PostMapping("/evaluate")
    public ResponseEntity<TrafficEvaluationResponse> evaluate(
             @RequestBody TrafficEvaluationRequest request
    ) {

        TrafficSubject subject =
                new TrafficSubject(
                        request.getSubject().getSubjectId(),
                        request.getSubject().getType()
                );

        TrafficRequest trafficRequest =
                new TrafficRequest(
                        request.getRequestId(),
                        subject,
                        request.getResource(),
                        request.getRequestedAt()
                );

        TrafficDecision decision =
                trafficControlService.evaluate(
                        trafficRequest
                );

        TrafficEvaluationResponse response =
                new TrafficEvaluationResponse(
                        decision.getStatus(),
                        decision.getReason(),
                        decision.getRemainingCapacity()
                );

        return ResponseEntity.ok(response);
    }
}
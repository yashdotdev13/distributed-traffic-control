package com.yashdotdev.distributed_traffic_control.traffic;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.*;

class DefaultTrafficControlServiceTest {

    @Test
    void shouldDelegateEvaluationToTrafficDecisionEngine() {

        TrafficDecisionEngine engine =
                mock(TrafficDecisionEngine.class);

        TrafficRequest request =
                mock(TrafficRequest.class);

        TrafficDecision expectedDecision =
                mock(TrafficDecision.class);

        when(engine.evaluate(request))
                .thenReturn(expectedDecision);

        DefaultTrafficControlService service =
                new DefaultTrafficControlService(engine);

        TrafficDecision actualDecision =
                service.evaluate(request);

        assertSame(
                expectedDecision,
                actualDecision
        );

        verify(engine)
                .evaluate(request);
    }

    @Test
    void shouldRejectNullRequest() {

        TrafficDecisionEngine engine =
                mock(TrafficDecisionEngine.class);

        DefaultTrafficControlService service =
                new DefaultTrafficControlService(engine);

        org.junit.jupiter.api.Assertions.assertThrows(
                NullPointerException.class,
                () -> service.evaluate(null)
        );
    }
}
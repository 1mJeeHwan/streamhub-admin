package org.streamhub.api.v1.order;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.streamhub.api.v1.delivery.adapter.Tracking;
import org.streamhub.api.v1.delivery.adapter.TrackingEvent;
import org.streamhub.api.v1.order.entity.OrderStatus;

/**
 * Unit tests for the courier-status → order-transition policy
 * ({@link OrderService#deliveryDrivenTransition}). Pure mapping, no state-machine wiring: a
 * delivered shipment advances SHIPPING→DONE, an in-transit one advances READY→SHIPPING, and
 * nothing else moves (idempotent / safe for the scheduler to call repeatedly).
 */
class OrderDeliveryTransitionTest {

    private static Tracking tracking(boolean completed, boolean hasEvents) {
        List<TrackingEvent> events = hasEvents
                ? List.of(new TrackingEvent("2026-06-18 09:00", "옥천HUB", "간선상차"))
                : List.of();
        return new Tracking("04", "CJ대한통운", "657606146365", 1, completed, "", "", events);
    }

    @Test
    void shippingAndCompleted_transitionsToDone() {
        assertThat(OrderService.deliveryDrivenTransition(OrderStatus.SHIPPING, tracking(true, true)))
                .contains(OrderStatus.DONE);
    }

    @Test
    void readyAndInTransit_transitionsToShipping() {
        assertThat(OrderService.deliveryDrivenTransition(OrderStatus.READY, tracking(false, true)))
                .contains(OrderStatus.SHIPPING);
    }

    @Test
    void shippingButNotCompleted_doesNotTransition() {
        assertThat(OrderService.deliveryDrivenTransition(OrderStatus.SHIPPING, tracking(false, true)))
                .isEmpty();
    }

    @Test
    void readyWithNoEvents_doesNotTransition() {
        assertThat(OrderService.deliveryDrivenTransition(OrderStatus.READY, tracking(false, false)))
                .isEmpty();
    }

    @Test
    void alreadyDone_doesNotTransition() {
        assertThat(OrderService.deliveryDrivenTransition(OrderStatus.DONE, tracking(true, true)))
                .isEmpty();
    }

    @Test
    void nullTracking_doesNotTransition() {
        assertThat(OrderService.deliveryDrivenTransition(OrderStatus.SHIPPING, null)).isEmpty();
    }
}

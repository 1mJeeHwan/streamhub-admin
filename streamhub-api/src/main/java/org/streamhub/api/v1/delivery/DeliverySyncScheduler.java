package org.streamhub.api.v1.delivery;

import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.streamhub.api.v1.order.OrderService;
import org.streamhub.api.v1.order.entity.Order;
import org.streamhub.api.v1.order.entity.OrderStatus;
import org.streamhub.api.v1.order.repository.OrderRepository;

/**
 * Polls in-transit (SHIPPING) orders and syncs each against the courier tracking API (C8), so a
 * shipment reported as 배달완료 advances the order to DONE without a manual step. Each order is
 * synced via the {@link OrderService} proxy (its own transaction); a failure on one order (e.g. an
 * invalid demo invoice) is logged and never aborts the batch.
 *
 * <p>Cron defaults to every 30 minutes; override with {@code app.delivery.sync-cron}.
 */
@Slf4j
@Component
public class DeliverySyncScheduler {

    private final OrderRepository orderRepository;
    private final OrderService orderService;

    public DeliverySyncScheduler(OrderRepository orderRepository, OrderService orderService) {
        this.orderRepository = orderRepository;
        this.orderService = orderService;
    }

    @Scheduled(cron = "${app.delivery.sync-cron:0 */30 * * * *}")
    public void syncShippingOrders() {
        List<Order> shipping = orderRepository.findByStatus(OrderStatus.SHIPPING);
        int checked = 0;
        for (Order order : shipping) {
            if (order.getTrackingNo() == null || order.getTrackingNo().isBlank()) {
                continue;
            }
            checked++;
            try {
                orderService.syncDelivery(order.getId());
            } catch (RuntimeException e) {
                log.debug("Delivery sync skipped order {}: {}", order.getOrderNo(), e.getMessage());
            }
        }
        if (checked > 0) {
            log.info("Delivery sync polled {} shipping order(s).", checked);
        }
    }
}

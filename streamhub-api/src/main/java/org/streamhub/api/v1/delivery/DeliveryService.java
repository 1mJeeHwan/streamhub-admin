package org.streamhub.api.v1.delivery;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.streamhub.api.base.exception.ApiException;
import org.streamhub.api.base.response.ResultCode;
import org.streamhub.api.v1.delivery.adapter.Carrier;
import org.streamhub.api.v1.delivery.adapter.DeliveryTrackingProvider;
import org.streamhub.api.v1.delivery.adapter.Tracking;
import org.streamhub.api.v1.delivery.adapter.TrackingEvent;
import org.streamhub.api.v1.order.entity.Order;
import org.streamhub.api.v1.order.repository.OrderRepository;

/**
 * Delivery tracking (C8): exposes the courier list and resolves an order's stored carrier +
 * invoice into a live shipment timeline via the {@link DeliveryTrackingProvider} seam. The carrier
 * code is taken from {@code order.shipCompany} when it already holds a code, mapped from a carrier
 * <i>name</i> for legacy data, or recommended from the invoice number as a last resort.
 */
@Service
public class DeliveryService {

    /** Shown when an order has no invoice yet (배송 준비/송장 미등록) — a graceful, non-error state. */
    private static final String PENDING_DESCRIPTION = "아직 송장 미등록";

    private final DeliveryTrackingProvider provider;
    private final OrderRepository orderRepository;

    public DeliveryService(DeliveryTrackingProvider provider, OrderRepository orderRepository) {
        this.provider = provider;
        this.orderRepository = orderRepository;
    }

    /** The supported courier list. */
    public List<Carrier> carriers() {
        return provider.carriers();
    }

    /**
     * Live shipment status for an order, using its stored carrier + invoice number. When the order
     * has no invoice yet (e.g. just purchased, not yet shipped) a graceful <i>pending</i>
     * {@link Tracking} is returned rather than an error — see {@link #trackOrder(Order)}.
     *
     * @throws ApiException {@code NOT_FOUND} if the order is missing, {@code INVALID_PARAMETER} if
     *                      the carrier cannot be determined for an existing invoice
     */
    @Transactional(readOnly = true)
    public Tracking trackOrder(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ApiException(ResultCode.NOT_FOUND));
        return trackOrder(order);
    }

    /**
     * Live shipment status for an already-loaded order (used by the member-facing flow). An order
     * with no invoice number yet degrades gracefully to a <i>pending</i> {@link Tracking} (empty
     * timeline, {@code level=0}, {@code completed=false}, a clear "아직 송장 미등록" note) so the
     * buy→track demo never 500s before an admin attaches the invoice.
     */
    public Tracking trackOrder(Order order) {
        String invoice = order.getTrackingNo();
        if (invoice == null || invoice.isBlank()) {
            return pendingTracking();
        }
        // Fetch the carrier list once: used to resolve the code and to backfill the display name.
        List<Carrier> carriers = carriers();
        String carrierCode = resolveCarrierCode(carriers, order.getShipCompany(), invoice);
        Tracking tracking = provider.track(carrierCode, invoice);
        String name = carrierName(carriers, carrierCode);
        return new Tracking(
                tracking.carrierCode(), name, tracking.invoiceNo(), tracking.level(),
                tracking.completed(), tracking.senderName(), tracking.receiverName(), tracking.events());
    }

    /**
     * A non-error placeholder shipment status for an order that has no invoice registered yet:
     * {@code level=0}, not completed, with a single self-describing "아직 송장 미등록" event so the
     * client can render a clear pending state instead of failing.
     */
    private Tracking pendingTracking() {
        return new Tracking(
                null, null, null, 0, false, "", "",
                List.of(new TrackingEvent(null, null, PENDING_DESCRIPTION)));
    }

    // --- helpers -----------------------------------------------------------

    /** Resolves a usable carrier code from the order's stored value (code or name) or the invoice. */
    private String resolveCarrierCode(List<Carrier> carriers, String shipCompany, String invoice) {
        Map<String, String> byCode = new LinkedHashMap<>();
        Map<String, String> byName = new LinkedHashMap<>();
        for (Carrier c : carriers) {
            byCode.put(c.code(), c.name());
            byName.put(c.name(), c.code());
        }
        if (shipCompany != null && !shipCompany.isBlank()) {
            if (byCode.containsKey(shipCompany)) {
                return shipCompany;
            }
            if (byName.containsKey(shipCompany)) {
                return byName.get(shipCompany);
            }
        }
        String recommended = provider.recommendCarrier(invoice);
        if (recommended == null || recommended.isBlank()) {
            throw new ApiException(ResultCode.INVALID_PARAMETER, "택배사를 확인할 수 없습니다");
        }
        return recommended;
    }

    private String carrierName(List<Carrier> carriers, String code) {
        for (Carrier c : carriers) {
            if (c.code().equals(code)) {
                return c.name();
            }
        }
        return code;
    }
}

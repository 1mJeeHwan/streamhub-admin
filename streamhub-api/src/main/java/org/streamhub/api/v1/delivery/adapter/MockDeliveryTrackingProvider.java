package org.streamhub.api.v1.delivery.adapter;

import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Mock delivery-tracking provider (C8) for offline/demo use — no external call. Returns a small
 * fixed carrier list and a deterministic three-step timeline. Active only when
 * {@code app.delivery.provider=mock}; the default is {@link SweetTrackerDeliveryProvider}.
 */
@Component
@ConditionalOnProperty(name = "app.delivery.provider", havingValue = "mock")
public class MockDeliveryTrackingProvider implements DeliveryTrackingProvider {

    private static final List<Carrier> CARRIERS = List.of(
            new Carrier("04", "CJ대한통운", false),
            new Carrier("05", "한진택배", false),
            new Carrier("08", "롯데택배", false),
            new Carrier("06", "로젠택배", false),
            new Carrier("01", "우체국택배", false));

    @Override
    public String code() {
        return "MOCK";
    }

    @Override
    public List<Carrier> carriers() {
        return CARRIERS;
    }

    @Override
    public String recommendCarrier(String invoice) {
        return "04";
    }

    @Override
    public Tracking track(String carrierCode, String invoice) {
        // A fully-delivered sample timeline (offline demo) — exercises the 배달완료 → DONE auto-sync.
        List<TrackingEvent> events = List.of(
                new TrackingEvent("2026-06-17 10:12", "서울강남", "집화처리"),
                new TrackingEvent("2026-06-17 21:40", "옥천HUB", "간선상차"),
                new TrackingEvent("2026-06-18 09:05", "수취인지역", "배달출발"),
                new TrackingEvent("2026-06-18 13:22", "수취인지역", "배달완료"));
        return new Tracking(carrierCode, null, invoice, 6, true, "", "", events);
    }
}

package org.streamhub.api.v1.worship.adapter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Default SMS notifier (demo/test mode): logs the intent and sends nothing. <b>No real SMS is
 * ever dispatched.</b> A real provider can be registered later via
 * {@code app.worship.sms.provider=aligo} (etc.); the sender number/template are that
 * provider's responsibility.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.worship.sms.provider", havingValue = "noop", matchIfMissing = true)
public class NoopSmsNotifier implements SmsNotifier {

    @Override
    public void notifyRegistrationReceived(String phone, String regNo) {
        log.info("[DEMO][SMS-noop] registration received → {} ({}) — not actually sent", mask(phone), regNo);
    }

    @Override
    public void notifyContacted(String phone, String regNo) {
        log.info("[DEMO][SMS-noop] contacted → {} ({}) — not actually sent", mask(phone), regNo);
    }

    /** Masks the middle group so applicant phone numbers never hit the logs in clear text. */
    private static String mask(String phone) {
        if (phone == null) {
            return "null";
        }
        String digits = phone.replaceAll("[^0-9]", "");
        if (digits.length() < 7) {
            return "***";
        }
        return digits.substring(0, 3) + "-****-" + digits.substring(digits.length() - 4);
    }
}

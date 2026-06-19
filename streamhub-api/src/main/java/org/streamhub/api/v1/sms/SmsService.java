package org.streamhub.api.v1.sms;

import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.streamhub.api.base.response.ResInfinityList;
import org.streamhub.api.v1.actionlog.ActionLogPublisher;
import org.streamhub.api.v1.order.entity.Order;
import org.streamhub.api.v1.sms.adapter.SmsSendCommand;
import org.streamhub.api.v1.sms.adapter.SmsSendResult;
import org.streamhub.api.v1.sms.adapter.SmsSender;
import org.streamhub.api.v1.sms.adapter.SmsSenderRouter;
import org.streamhub.api.v1.sms.dto.SmsListItem;
import org.streamhub.api.v1.sms.dto.SmsSearchRequest;
import org.streamhub.api.v1.sms.dto.SmsSendRequest;
import org.streamhub.api.v1.sms.entity.SmsChannel;
import org.streamhub.api.v1.sms.entity.SmsKind;
import org.streamhub.api.v1.sms.entity.SmsMessage;
import org.streamhub.api.v1.sms.mapper.SmsMapper;
import org.streamhub.api.v1.sms.repository.SmsMessageRepository;

/**
 * SMS history listing (MyBatis), admin custom send, and auto-notification helpers
 * ({@link #sendForOrder}/{@link #sendForDonation}) wired into the order/donation flows.
 *
 * <p><b>No real SMS is ever dispatched.</b> The configured {@link SmsSender} (mock by default)
 * performs no external call; this service persists the {@code SMS_MESSAGE} row with masked
 * recipient and {@code testMode=Y}. Auto-notification callers invoke best-effort and swallow
 * failures (same철학 as {@code ActionLogPublisher}) so a notification never breaks the order.
 *
 * <p>The auto-notification helpers run in {@link Propagation#REQUIRES_NEW}: SMS persistence and
 * dispatch happen in an independent transaction, so a sender failure rolls back only the SMS row
 * and never marks the parent order/donation transaction rollback-only. Combined with the caller's
 * try/catch, the parent always commits regardless of notification outcome.
 */
@Service
public class SmsService {

    private final SmsMapper smsMapper;
    private final SmsMessageRepository smsMessageRepository;
    private final SmsSenderRouter smsSenderRouter;
    private final ActionLogPublisher actionLogPublisher;

    public SmsService(
            SmsMapper smsMapper,
            SmsMessageRepository smsMessageRepository,
            SmsSenderRouter smsSenderRouter,
            ActionLogPublisher actionLogPublisher) {
        this.smsMapper = smsMapper;
        this.smsMessageRepository = smsMessageRepository;
        this.smsSenderRouter = smsSenderRouter;
        this.actionLogPublisher = actionLogPublisher;
    }

    @Transactional(readOnly = true)
    public ResInfinityList<SmsListItem> list(SmsSearchRequest request) {
        String keyword = blankToNull(request.keyword());
        String kind = request.kind() == null ? null : request.kind().name();
        int size = request.pageSizeOrDefault();

        List<SmsListItem> contents =
                smsMapper.selectList(keyword, kind, request.from(), request.to(), request.offset(), size);
        long total = smsMapper.countList(keyword, kind, request.from(), request.to());
        return ResInfinityList.of(contents, total, size);
    }

    /** Admin custom send: persists a {@code CUSTOM} message (masked number, mock dispatch). */
    @Transactional
    public SmsListItem send(SmsSendRequest request) {
        SmsMessage saved = persist(
                request.toNumber(), request.content(), SmsKind.CUSTOM,
                request.memberId(), null, null);
        actionLogPublisher.publish("SMS_SEND", "SMS", String.valueOf(saved.getId()), maskNumber(request.toNumber()));
        return SmsListItem.from(saved);
    }

    /**
     * Auto-notification for an order event ({@code ORDER_PAID} / {@code ORDER_SHIPPING}).
     * Best-effort: the caller wraps this in try/catch — it never throws into the order tx.
     *
     * <p>Runs in {@link Propagation#REQUIRES_NEW} so a sender failure rolls back only this
     * independent SMS transaction and cannot poison the parent order transaction.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public SmsListItem sendForOrder(Order order, SmsKind kind) {
        String content = orderBody(kind, order.getOrderNo());
        SmsMessage saved = persist(
                order.getReceiverPhone() != null ? order.getReceiverPhone() : order.getOrderedPhone(),
                content, kind, order.getMemberId(), "ORDER", String.valueOf(order.getId()));
        return SmsListItem.from(saved);
    }

    /**
     * Auto-notification for a one-off donation ({@code DONATION_ONCE}). Best-effort.
     *
     * <p>Runs in {@link Propagation#REQUIRES_NEW} so a sender failure rolls back only this
     * independent SMS transaction and cannot poison the parent donation transaction.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public SmsListItem sendForDonation(Long memberId, Long donationId, Long amount) {
        String content = "[StreamHub] ₩" + amount + " 후원이 정상 접수되었습니다. 감사합니다. (테스트발송)";
        SmsMessage saved = persist(
                null, content, SmsKind.DONATION_ONCE, memberId, "DONATION", String.valueOf(donationId));
        return SmsListItem.from(saved);
    }

    // --- helpers -----------------------------------------------------------

    /** Masks, resolves channel, dispatches via the (mock) sender, and persists the row. */
    private SmsMessage persist(String rawNumber, String content, SmsKind kind,
                               Long memberId, String refType, String refId) {
        String masked = maskNumber(rawNumber);
        SmsChannel channel = resolveChannel(content);
        SmsSender sender = smsSenderRouter.resolve();
        // The dispatcher needs the real recipient (so a real provider could actually deliver);
        // only the persisted/displayed copy is masked. The mock sender ignores it.
        SmsSendResult result = sender.send(new SmsSendCommand(rawNumber, content, channel));
        return smsMessageRepository.save(SmsMessage.builder()
                .toNumber(masked)
                .content(content)
                .kind(kind)
                .channel(channel)
                .sender(result.sender())
                .status(result.status())
                .testMode("Y")
                .memberId(memberId)
                .refType(refType)
                .refId(refId)
                .build());
    }

    /** {@code >90} EUC-KR bytes ⇒ LMS, else SMS (delegates to {@link SmsChannelPolicy}). */
    SmsChannel resolveChannel(String content) {
        return SmsChannelPolicy.resolve(content);
    }

    /** Masks the last 4 digits of a phone number ({@code 010-1234-****}); null/blank ⇒ placeholder. */
    String maskNumber(String number) {
        if (number == null || number.isBlank()) {
            return "010-0000-****";
        }
        String digits = number.replaceAll("[^0-9]", "");
        if (digits.length() < 4) {
            return "010-0000-****";
        }
        String head = digits.substring(0, digits.length() - 4);
        StringBuilder sb = new StringBuilder();
        if (head.length() >= 7) {
            sb.append(head, 0, 3).append('-').append(head.substring(3));
        } else {
            sb.append(head);
        }
        return sb.append("-****").toString();
    }

    private String orderBody(SmsKind kind, String orderNo) {
        return switch (kind) {
            case ORDER_PAID -> "[StreamHub] " + orderNo + " 결제가 완료되었습니다. (테스트발송)";
            case ORDER_SHIPPING -> "[StreamHub] " + orderNo + " 상품이 배송 시작되었습니다. 운송장은 주문내역에서 확인하세요. (테스트발송)";
            default -> "[StreamHub] " + orderNo + " 주문 알림입니다. (테스트발송)";
        };
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}

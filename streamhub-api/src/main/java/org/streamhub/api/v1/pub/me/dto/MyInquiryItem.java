package org.streamhub.api.v1.pub.me.dto;

import java.time.LocalDateTime;
import org.streamhub.api.v1.goods.inquiry.entity.AnswerStatus;

/**
 * One row of the member's own goods inquiries ("내 문의"), goods name joined.
 *
 * @param goodsId   inquired goods item
 * @param goodsName goods name ({@code null} if the goods was deleted)
 * @param question  the inquiry body
 * @param answer    the admin's answer ({@code null} while unanswered)
 * @param status    WAITING or ANSWERED
 * @param createdAt when it was asked
 */
public record MyInquiryItem(
        Long goodsId,
        String goodsName,
        String question,
        String answer,
        AnswerStatus status,
        LocalDateTime createdAt) {
}

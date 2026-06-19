package org.streamhub.api.v1.pub.me.dto;

import java.time.LocalDateTime;

/**
 * One row of the member's own goods reviews ("내 후기"), goods name joined.
 *
 * @param goodsId   reviewed goods item
 * @param goodsName goods name ({@code null} if the goods was deleted)
 * @param rating    star rating 1–5
 * @param content   review body
 * @param createdAt when it was written
 */
public record MyReviewItem(
        Long goodsId,
        String goodsName,
        int rating,
        String content,
        LocalDateTime createdAt) {
}

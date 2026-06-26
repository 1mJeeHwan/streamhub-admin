package org.streamhub.api.v1.chat.feature;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * One feature catalog entry — the chatbot's knowledge source for "이 기능 있나요? / 어떻게 쓰나요?".
 * Mirrors {@code streamhub-web/src/lib/features.catalog.ts} (kept in sync) plus a {@code howTo}
 * usage guide. Loaded from {@code resources/feature-catalog.json} by {@link FeatureCatalogService}.
 *
 * @param id       stable slug (e.g. {@code orders})
 * @param domain   one of support/shop/content/member/community/marketing/settings
 * @param title    Korean screen name
 * @param summary  one-line description
 * @param status   honesty status: {@code live} / {@code demo} / {@code external}
 * @param href     in-app route (admin or user site)
 * @param howTo    1–3 sentence usage guide surfaced by the chatbot
 * @param keywords space/comma-separated synonyms in the words users actually type (colloquial +
 *                 admin terms), so the chatbot's keyword search reaches this feature however the
 *                 question is phrased. May be {@code null} for older entries.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record FeatureInfo(
        String id,
        String domain,
        String title,
        String summary,
        String status,
        String href,
        String howTo,
        String keywords) {
}

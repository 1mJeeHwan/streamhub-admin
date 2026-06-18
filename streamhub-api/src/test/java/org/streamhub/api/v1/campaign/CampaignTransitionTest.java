package org.streamhub.api.v1.campaign;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.streamhub.api.base.exception.ApiException;
import org.streamhub.api.base.response.ResultCode;
import org.streamhub.api.v1.campaign.entity.Campaign;
import org.streamhub.api.v1.campaign.entity.CampaignStatus;
import org.streamhub.api.v1.campaign.entity.CampaignType;

/**
 * Table-based unit tests for the campaign lifecycle state machine
 * ({@link Campaign#isLegalTransition} / {@link Campaign#changeStatus}). Verifies that legal
 * transitions are accepted, illegal jumps are rejected with {@link ApiException}
 * {@code INVALID_PARAMETER}, and terminal {@code ENDED} is absorbing.
 */
class CampaignTransitionTest {

    @ParameterizedTest(name = "{0} → {1} is legal")
    @CsvSource({
            "DRAFT,  ACTIVE",
            "DRAFT,  ENDED",
            "ACTIVE, ENDED"
    })
    void legalTransition_isAccepted(CampaignStatus from, CampaignStatus to) {
        assertThat(Campaign.isLegalTransition(from, to)).isTrue();

        Campaign campaign = campaignWith(from);
        campaign.changeStatus(to);
        assertThat(campaign.getStatus()).isEqualTo(to);
    }

    @ParameterizedTest(name = "{0} → {1} is illegal")
    @CsvSource({
            // No going back / re-opening, and ENDED is absorbing (terminal).
            "ACTIVE, DRAFT",
            "ENDED,  DRAFT",
            "ENDED,  ACTIVE",
            "ENDED,  ENDED",
            // No-op self transitions are not declared legal.
            "DRAFT,  DRAFT",
            "ACTIVE, ACTIVE"
    })
    void illegalTransition_isRejected(CampaignStatus from, CampaignStatus to) {
        assertThat(Campaign.isLegalTransition(from, to)).isFalse();

        Campaign campaign = campaignWith(from);
        assertThatThrownBy(() -> campaign.changeStatus(to))
                .isInstanceOf(ApiException.class)
                .extracting("resultCode")
                .isEqualTo(ResultCode.INVALID_PARAMETER);
        // State is left unchanged after a rejected transition.
        assertThat(campaign.getStatus()).isEqualTo(from);
    }

    @Test
    void nullTarget_isRejected() {
        assertThat(Campaign.isLegalTransition(CampaignStatus.DRAFT, null)).isFalse();

        Campaign campaign = campaignWith(CampaignStatus.DRAFT);
        assertThatThrownBy(() -> campaign.changeStatus(null))
                .isInstanceOf(ApiException.class)
                .extracting("resultCode")
                .isEqualTo(ResultCode.INVALID_PARAMETER);
    }

    @Test
    void nullSource_permitsOnlyInitialDraft() {
        // A never-persisted campaign (no current status) may only start as DRAFT.
        assertThat(Campaign.isLegalTransition(null, CampaignStatus.DRAFT)).isTrue();
        assertThat(Campaign.isLegalTransition(null, CampaignStatus.ACTIVE)).isFalse();
        assertThat(Campaign.isLegalTransition(null, CampaignStatus.ENDED)).isFalse();
    }

    private Campaign campaignWith(CampaignStatus status) {
        return Campaign.builder()
                .title("데모 캠페인")
                .type(CampaignType.EVENT)
                .startAt(LocalDateTime.now())
                .endAt(LocalDateTime.now().plusDays(7))
                .status(status)
                .build();
    }
}

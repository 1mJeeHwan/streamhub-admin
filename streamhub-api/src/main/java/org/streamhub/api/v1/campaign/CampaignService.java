package org.streamhub.api.v1.campaign;

import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.streamhub.api.base.exception.ApiException;
import org.streamhub.api.base.response.ResultCode;
import org.streamhub.api.v1.campaign.dto.CampaignDto;
import org.streamhub.api.v1.campaign.dto.CampaignSearchRequest;
import org.streamhub.api.v1.campaign.entity.Campaign;
import org.streamhub.api.v1.campaign.entity.CampaignStatus;
import org.streamhub.api.v1.campaign.repository.CampaignRepository;

/**
 * Campaign/event management: admin CRUD plus lifecycle status transitions. The demo dataset is
 * small, so the listing loads all campaigns and filters/sorts in memory by id (newest first) — no
 * pagination or external index needed.
 *
 * <p><b>Demo scope (honesty note):</b> this service manages campaign <em>definitions</em> and their
 * lifecycle status only. It has <b>no execution engine</b> — there is no audience send (SMS/push)
 * and no fund-raised tracking. Activating a campaign records a status; it does not deliver anything
 * to members or move money. Real audience delivery would require the SMS/push providers (demo-gated)
 * and fund tracking would require the donation/payment ledger; both are intentionally out of scope.
 *
 * <p>{@link #changeStatus} delegates legality to the entity state machine
 * ({@link Campaign#changeStatus}), which rejects illegal transitions with
 * {@link ApiException} {@code INVALID_PARAMETER}. Terminal states are absorbing.
 */
@Service
public class CampaignService {

    private final CampaignRepository campaignRepository;

    public CampaignService(CampaignRepository campaignRepository) {
        this.campaignRepository = campaignRepository;
    }

    /** Admin listing: all campaigns, newest first; optional type/status filters. */
    @Transactional(readOnly = true)
    public List<CampaignDto> list(CampaignSearchRequest request) {
        return campaignRepository.findAll().stream()
                .filter(campaign -> request == null || request.type() == null
                        || request.type() == campaign.getType())
                .filter(campaign -> request == null || request.status() == null
                        || request.status() == campaign.getStatus())
                .sorted(Comparator.comparing(Campaign::getId).reversed())
                .map(CampaignDto::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public CampaignDto get(Long id) {
        Campaign campaign = campaignRepository.findById(id)
                .orElseThrow(() -> new ApiException(ResultCode.NOT_FOUND));
        return CampaignDto.from(campaign);
    }

    @Transactional
    public CampaignDto create(CampaignDto request) {
        Campaign campaign = Campaign.builder()
                .title(request.getTitle())
                .type(request.getType())
                .description(request.getDescription())
                .bannerImageUrl(request.getBannerImageUrl())
                .linkedGoodsIds(request.getLinkedGoodsIds())
                .targetAmount(request.getTargetAmount())
                .startAt(request.getStartAt())
                .endAt(request.getEndAt())
                .status(request.getStatus())
                .build();
        Campaign saved = campaignRepository.save(campaign);
        return CampaignDto.from(saved);
    }

    @Transactional
    public CampaignDto update(Long id, CampaignDto request) {
        Campaign campaign = campaignRepository.findById(id)
                .orElseThrow(() -> new ApiException(ResultCode.NOT_FOUND));
        campaign.update(
                request.getTitle(), request.getType(), request.getDescription(),
                request.getBannerImageUrl(), request.getLinkedGoodsIds(), request.getTargetAmount(),
                request.getStartAt(), request.getEndAt(), request.getStatus());
        campaignRepository.saveAndFlush(campaign);
        return CampaignDto.from(campaign);
    }

    /**
     * Transitions a campaign through its lifecycle state machine. Legality is enforced by the
     * entity ({@link Campaign#changeStatus}); illegal jumps (e.g. {@code DRAFT → } an undefined
     * target, or any transition out of the terminal {@code ENDED}) are rejected.
     *
     * @throws ApiException {@code NOT_FOUND} if the campaign is missing, or {@code INVALID_PARAMETER}
     *                      for an illegal transition
     */
    @Transactional
    public CampaignDto changeStatus(Long id, CampaignStatus status) {
        Campaign campaign = campaignRepository.findById(id)
                .orElseThrow(() -> new ApiException(ResultCode.NOT_FOUND));
        campaign.changeStatus(status);
        campaignRepository.saveAndFlush(campaign);
        return CampaignDto.from(campaign);
    }

    @Transactional
    public void delete(Long id) {
        Campaign campaign = campaignRepository.findById(id)
                .orElseThrow(() -> new ApiException(ResultCode.NOT_FOUND));
        campaignRepository.delete(campaign);
    }
}

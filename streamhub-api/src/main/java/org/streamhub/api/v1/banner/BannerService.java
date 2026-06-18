package org.streamhub.api.v1.banner;

import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.streamhub.api.base.exception.ApiException;
import org.streamhub.api.base.response.ResultCode;
import org.streamhub.api.v1.actionlog.ActionLogPublisher;
import org.streamhub.api.v1.banner.dto.BannerDto;
import org.streamhub.api.v1.banner.dto.BannerSearchRequest;
import org.streamhub.api.v1.banner.entity.Banner;
import org.streamhub.api.v1.banner.repository.BannerRepository;

/**
 * Banner management: admin CRUD plus a drag-to-reorder action. The demo dataset is small, so the
 * listing loads all banners (ordered by sortOrder then id) and applies the optional filters in
 * memory — no dynamic query needed.
 */
@Service
public class BannerService {

    private final BannerRepository bannerRepository;
    private final ActionLogPublisher actionLogPublisher;

    public BannerService(BannerRepository bannerRepository, ActionLogPublisher actionLogPublisher) {
        this.bannerRepository = bannerRepository;
        this.actionLogPublisher = actionLogPublisher;
    }

    /** Admin listing: all banners ordered by sortOrder then id, with optional filters. */
    @Transactional(readOnly = true)
    public List<BannerDto> list(BannerSearchRequest request) {
        return bannerRepository.findAllByOrderBySortOrderAscIdAsc().stream()
                .filter(banner -> request == null || request.position() == null
                        || request.position() == banner.getPosition())
                .filter(banner -> request == null || request.device() == null
                        || request.device() == banner.getDevice())
                .filter(banner -> request == null || request.useYn() == null
                        || request.useYn().isBlank() || request.useYn().equals(banner.getUseYn()))
                .map(BannerDto::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public BannerDto getDetail(Long id) {
        Banner banner = bannerRepository.findById(id)
                .orElseThrow(() -> new ApiException(ResultCode.NOT_FOUND));
        return BannerDto.from(banner);
    }

    @Transactional
    public BannerDto create(BannerDto request) {
        Banner banner = Banner.builder()
                .title(request.getTitle())
                .position(request.getPosition())
                .device(request.getDevice())
                .imageUrl(request.getImageUrl())
                .linkUrl(request.getLinkUrl())
                .startAt(request.getStartAt())
                .endAt(request.getEndAt())
                .sortOrder(request.getSortOrder())
                .useYn(defaultYn(request.getUseYn()))
                .build();
        Banner saved = bannerRepository.save(banner);
        actionLogPublisher.publish("BANNER_CREATE", "BANNER", String.valueOf(saved.getId()), request.getTitle());
        return BannerDto.from(saved);
    }

    @Transactional
    public BannerDto update(Long id, BannerDto request) {
        Banner banner = bannerRepository.findById(id)
                .orElseThrow(() -> new ApiException(ResultCode.NOT_FOUND));
        banner.update(
                request.getTitle(), request.getPosition(), request.getDevice(), request.getImageUrl(),
                request.getLinkUrl(), request.getStartAt(), request.getEndAt(), request.getSortOrder(),
                defaultYn(request.getUseYn()));
        bannerRepository.saveAndFlush(banner);
        actionLogPublisher.publish("BANNER_UPDATE", "BANNER", String.valueOf(id), request.getTitle());
        return BannerDto.from(banner);
    }

    @Transactional
    public BannerDto updateSortOrder(Long id, int sortOrder) {
        Banner banner = bannerRepository.findById(id)
                .orElseThrow(() -> new ApiException(ResultCode.NOT_FOUND));
        banner.updateSortOrder(sortOrder);
        bannerRepository.saveAndFlush(banner);
        actionLogPublisher.publish("BANNER_SORT", "BANNER", String.valueOf(id), banner.getTitle());
        return BannerDto.from(banner);
    }

    @Transactional
    public void delete(Long id) {
        Banner banner = bannerRepository.findById(id)
                .orElseThrow(() -> new ApiException(ResultCode.NOT_FOUND));
        bannerRepository.delete(banner);
        actionLogPublisher.publish("BANNER_DELETE", "BANNER", String.valueOf(id), banner.getTitle());
    }

    // --- helpers -----------------------------------------------------------

    private String defaultYn(String value) {
        return value == null || value.isBlank() ? "Y" : value;
    }
}

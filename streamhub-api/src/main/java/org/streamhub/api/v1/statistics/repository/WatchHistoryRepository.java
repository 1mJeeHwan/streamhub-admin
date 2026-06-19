package org.streamhub.api.v1.statistics.repository;

import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.streamhub.api.v1.statistics.entity.WatchHistory;

/** JPA repository for {@link WatchHistory}. Aggregations use MyBatis (StatMapper). */
public interface WatchHistoryRepository extends JpaRepository<WatchHistory, Long> {

    /** A member's watch events, most recent first (page-bounded — the "내 시청기록" feed). */
    List<WatchHistory> findByMemberIdOrderByWatchedAtDesc(Long memberId, Pageable pageable);
}

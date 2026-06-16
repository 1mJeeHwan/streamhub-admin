package org.streamhub.api.v1.statistics.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.streamhub.api.v1.statistics.entity.WatchHistory;

/** JPA repository for {@link WatchHistory}. Aggregations use MyBatis (StatMapper). */
public interface WatchHistoryRepository extends JpaRepository<WatchHistory, Long> {
}

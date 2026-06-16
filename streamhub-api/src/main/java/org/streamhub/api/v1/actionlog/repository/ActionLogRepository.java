package org.streamhub.api.v1.actionlog.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.streamhub.api.v1.actionlog.entity.ActionLog;

/** JPA repository for {@link ActionLog} (writes from the SQS consumer + seed). */
public interface ActionLogRepository extends JpaRepository<ActionLog, Long> {
}

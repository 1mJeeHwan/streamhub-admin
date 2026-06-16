package org.streamhub.api.v1.content.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.streamhub.api.v1.content.entity.Channel;

/** JPA repository for {@link Channel}. */
public interface ChannelRepository extends JpaRepository<Channel, Long> {
}

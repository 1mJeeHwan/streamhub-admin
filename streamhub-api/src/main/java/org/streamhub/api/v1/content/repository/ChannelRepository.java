package org.streamhub.api.v1.content.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.streamhub.api.v1.content.entity.Channel;

/** JPA repository for {@link Channel}. */
public interface ChannelRepository extends JpaRepository<Channel, Long> {

    /** Channels owned by a church (CHURCH_MANAGER scope filter). */
    List<Channel> findByChurchId(Long churchId);
}

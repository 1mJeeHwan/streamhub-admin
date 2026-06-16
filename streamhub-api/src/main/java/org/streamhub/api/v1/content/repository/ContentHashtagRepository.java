package org.streamhub.api.v1.content.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.streamhub.api.v1.content.entity.ContentHashtag;

/** JPA repository for the content-hashtag join. */
public interface ContentHashtagRepository extends JpaRepository<ContentHashtag, Long> {

    List<ContentHashtag> findByContentId(Long contentId);

    void deleteByContentId(Long contentId);
}

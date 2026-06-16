package org.streamhub.api.v1.content.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.streamhub.api.v1.content.entity.ContentFile;

/** JPA repository for {@link ContentFile}. */
public interface ContentFileRepository extends JpaRepository<ContentFile, Long> {

    List<ContentFile> findByContentId(Long contentId);

    void deleteByContentId(Long contentId);
}

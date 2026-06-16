package org.streamhub.api.v1.post.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.streamhub.api.v1.post.entity.Post;

/** JPA repository for {@link Post} (detail/CRUD). Listing/search uses MyBatis. */
public interface PostRepository extends JpaRepository<Post, Long> {
}

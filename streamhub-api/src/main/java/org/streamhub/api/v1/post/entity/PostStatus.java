package org.streamhub.api.v1.post.entity;

/** Publication state of a post. Mirrors content publication semantics but kept per-domain. */
public enum PostStatus {
    DRAFT,
    PUBLISHED
}

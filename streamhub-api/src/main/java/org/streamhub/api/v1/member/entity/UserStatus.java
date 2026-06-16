package org.streamhub.api.v1.member.entity;

/**
 * Lifecycle state of a managed member account.
 */
public enum UserStatus {
    /** Signed up, awaiting operator approval. */
    PENDING,
    /** Approved and active. */
    CONFIRMED,
    /** Deactivated (rejected or suspended). */
    INACTIVE
}

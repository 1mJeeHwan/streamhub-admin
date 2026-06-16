package org.streamhub.api.v1.actionlog.dto;

/**
 * The payload published to SQS for one admin action. The consumer stamps the
 * persisted time itself, so no timestamp is carried here (keeps JSON trivial).
 *
 * @param adminId    operator id (null for pre-auth events)
 * @param adminName  operator name
 * @param action     action code (LOGIN, MEMBER_APPROVE, CONTENT_DELETE, ...)
 * @param targetType affected entity type (MEMBER, CONTENT, ...)
 * @param targetId   affected entity id (as string; may be a comma list for bulk)
 * @param detail     human-readable detail
 */
public record ActionLogMessage(
        Long adminId,
        String adminName,
        String action,
        String targetType,
        String targetId,
        String detail) {
}

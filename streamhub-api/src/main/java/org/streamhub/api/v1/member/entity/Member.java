package org.streamhub.api.v1.member.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * A managed end-user (member of a church). Distinct from {@code AdminAccount}
 * (the operators who manage these members).
 */
@Entity
@Table(name = "MEMBER", indexes = {
        @Index(name = "idx_member_church", columnList = "church_id"),
        @Index(name = "idx_member_status", columnList = "user_status"),
        @Index(name = "idx_member_created", columnList = "created_at")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Member {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "church_id", nullable = false)
    private Long churchId;

    @Column(name = "email", nullable = false, unique = true, length = 100)
    private String email;

    @Column(name = "password", nullable = false, length = 100)
    private String password;

    @Column(name = "name", nullable = false, length = 50)
    private String name;

    @Column(name = "phone", length = 20)
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(name = "user_status", nullable = false, length = 20)
    private UserStatus userStatus;

    /** Whether the member is permitted to watch live broadcasts. */
    @Column(name = "live_yn", nullable = false, length = 1)
    private String liveYn;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    private Member(Long churchId, String email, String password, String name,
                   String phone, UserStatus userStatus, String liveYn, LocalDateTime createdAt) {
        this.churchId = churchId;
        this.email = email;
        this.password = password;
        this.name = name;
        this.phone = phone;
        this.userStatus = userStatus;
        this.liveYn = liveYn;
        this.createdAt = createdAt != null ? createdAt : LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }

    /** Updates editable profile fields. */
    public void updateProfile(String name, String phone, String liveYn) {
        this.name = name;
        this.phone = phone;
        this.liveYn = liveYn;
        this.updatedAt = LocalDateTime.now();
    }

    /** Transitions the member's lifecycle status. */
    public void changeStatus(UserStatus userStatus) {
        this.userStatus = userStatus;
        this.updatedAt = LocalDateTime.now();
    }
}

package org.streamhub.api.v1.admin.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Operator account used to log into the admin site.
 *
 * <p>Deliberately separate from {@code Member} (the end users being managed): this models
 * who operates the console, not who is administered.
 */
@Entity
@Table(name = "ADMIN_ACCOUNT")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AdminAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "login_id", nullable = false, unique = true, length = 50)
    private String loginId;

    @Column(name = "password", nullable = false, length = 100)
    private String password;

    @Column(name = "name", nullable = false, length = 50)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 30)
    private Role role;

    /** Null for SYSTEM accounts; set for CHURCH_MANAGER accounts. */
    @Column(name = "church_id")
    private Long churchId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Builder
    private AdminAccount(String loginId, String password, String name, Role role, Long churchId) {
        this.loginId = loginId;
        this.password = password;
        this.name = name;
        this.role = role;
        this.churchId = churchId;
        this.createdAt = LocalDateTime.now();
    }
}

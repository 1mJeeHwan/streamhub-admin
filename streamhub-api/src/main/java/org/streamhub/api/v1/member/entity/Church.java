package org.streamhub.api.v1.member.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** A church belonging to a {@link Region}; members are scoped to a church. */
@Entity
@Table(name = "CHURCH")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Church {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "region_id", nullable = false)
    private Long regionId;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "open_yn", nullable = false, length = 1)
    private String openYn;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Builder
    private Church(Long regionId, String name, String openYn) {
        this.regionId = regionId;
        this.name = name;
        this.openYn = openYn;
        this.createdAt = LocalDateTime.now();
    }
}

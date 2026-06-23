package org.streamhub.api.v1.playlist.entity;

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

/**
 * A curated playlist — an admin-assembled, ordered collection of tracks for free listening on the
 * music tab. The tracks themselves live in {@code PLAYLIST_TRACK} (a playlist references existing
 * album tracks; it owns ordering, not the audio).
 */
@Entity
@Table(name = "PLAYLIST")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Playlist {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "title", nullable = false, length = 150)
    private String title;

    @Column(name = "description", length = 1000, columnDefinition = "TEXT")
    private String description;

    @Column(name = "cover_key", length = 500)
    private String coverKey;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "use_yn", nullable = false, length = 1)
    private String useYn;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    private Playlist(String title, String description, String coverKey, Integer sortOrder, String useYn) {
        this.title = title;
        this.description = description;
        this.coverKey = coverKey;
        this.sortOrder = sortOrder == null ? 0 : sortOrder;
        this.useYn = useYn == null ? "Y" : useYn;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }

    public void update(String title, String description, String coverKey, Integer sortOrder, String useYn) {
        this.title = title;
        this.description = description;
        this.coverKey = coverKey;
        this.sortOrder = sortOrder == null ? this.sortOrder : sortOrder;
        this.useYn = useYn == null ? this.useYn : useYn;
        this.updatedAt = LocalDateTime.now();
    }
}

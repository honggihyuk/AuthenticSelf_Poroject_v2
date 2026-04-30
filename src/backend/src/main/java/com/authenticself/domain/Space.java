package com.authenticself.domain;

import com.authenticself.space.PreferredStyle;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * JPA mapping for the {@code spaces} table (PRD §3, §7; V1 + V2 migrations).
 * <p>
 * Two lifecycle phases per PRD §6 UC-01:
 * <ol>
 *   <li>Upload (this task, UC-01-photo-upload): fills identity + file columns,
 *       sets {@link Status#PENDING_ANALYSIS}. Analysis columns remain NULL.</li>
 *   <li>Analysis (UC-01-space-analysis): fills {@code dimensions}, {@code mainColor},
 *       {@code style}, {@code analysisDate} and flips {@code status} to
 *       {@link Status#ANALYZED} or {@link Status#FAILED}.</li>
 * </ol>
 * No {@code @ManyToOne} relations yet — this entity still conforms to the
 * stub discipline from Task 1; relations are added in later UC tasks.
 */
@Entity
@Table(name = "spaces")
public class Space {

    /** Matches the native MySQL {@code ENUM} declared in V2. */
    public enum Status {
        PENDING_ANALYSIS,
        ANALYZED,
        FAILED
    }

    @Id
    @Column(name = "room_id", nullable = false, length = 64)
    private String roomId;

    @Column(name = "user_id", nullable = false, length = 64)
    private String userId;

    @Column(name = "photo_url", nullable = false, length = 512)
    private String photoUrl;

    @Column(name = "original_filename", length = 255)
    private String originalFilename;

    @Column(name = "content_type", nullable = false, length = 64)
    private String contentType;

    @Column(name = "file_size_bytes", nullable = false)
    private Long fileSizeBytes;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false,
            columnDefinition = "ENUM('PENDING_ANALYSIS','ANALYZED','FAILED')")
    private Status status;

    @Column(name = "uploaded_at", nullable = false)
    private LocalDateTime uploadedAt;

    // --- Analysis columns — populated later by UC-01-space-analysis -----
    @Column(name = "dimensions", length = 100)
    private String dimensions;

    @Column(name = "main_color", length = 32)
    private String mainColor;

    @Column(name = "style", length = 64)
    private String style;

    // UC-01-style-selection (Task 4) FR-10 / AC-18 — user's chosen target
    // style. NULL means the user has not yet selected a preferred style.
    // The V3 migration defines the column as VARCHAR(32) NULL.
    // columnDefinition pins the JDBC type to VARCHAR so Hibernate 6.4's
    // MySQL native-ENUM default for @Enumerated(STRING) does not falsely
    // flag a schema mismatch under ddl-auto=validate.
    @Enumerated(EnumType.STRING)
    @Column(name = "preferred_style", length = 32, nullable = true,
            columnDefinition = "VARCHAR(32)")
    private PreferredStyle preferredStyle;

    @Column(name = "analysis_date")
    private LocalDateTime analysisDate;

    // ---------- accessors ------------------------------------------------
    public String getRoomId()           { return roomId; }
    public void   setRoomId(String v)   { this.roomId = v; }

    public String getUserId()           { return userId; }
    public void   setUserId(String v)   { this.userId = v; }

    public String getPhotoUrl()         { return photoUrl; }
    public void   setPhotoUrl(String v) { this.photoUrl = v; }

    public String getOriginalFilename()         { return originalFilename; }
    public void   setOriginalFilename(String v) { this.originalFilename = v; }

    public String getContentType()         { return contentType; }
    public void   setContentType(String v) { this.contentType = v; }

    public Long getFileSizeBytes()         { return fileSizeBytes; }
    public void setFileSizeBytes(Long v)   { this.fileSizeBytes = v; }

    public Status getStatus()         { return status; }
    public void   setStatus(Status v) { this.status = v; }

    public LocalDateTime getUploadedAt()         { return uploadedAt; }
    public void          setUploadedAt(LocalDateTime v) { this.uploadedAt = v; }

    public String getDimensions()         { return dimensions; }
    public void   setDimensions(String v) { this.dimensions = v; }

    public String getMainColor()         { return mainColor; }
    public void   setMainColor(String v) { this.mainColor = v; }

    public String getStyle()         { return style; }
    public void   setStyle(String v) { this.style = v; }

    public PreferredStyle getPreferredStyle()             { return preferredStyle; }
    public void           setPreferredStyle(PreferredStyle v) { this.preferredStyle = v; }

    public LocalDateTime getAnalysisDate()         { return analysisDate; }
    public void          setAnalysisDate(LocalDateTime v) { this.analysisDate = v; }
}

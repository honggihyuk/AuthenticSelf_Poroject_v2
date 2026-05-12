package com.authenticself.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Phase B cache row. One per (curated furniture, external source, external id).
 * Populated by {@code SimilarProductsBatch}; read by the public
 * {@code GET /api/v1/furniture/{id}/similar} endpoint.
 */
@Entity
@Table(name = "furniture_similar_cache")
@IdClass(FurnitureSimilarCache.PK.class)
public class FurnitureSimilarCache {

    @Id
    @Column(name = "furniture_id", nullable = false, length = 64)
    private String furnitureId;

    @Id
    @Column(name = "source", nullable = false, length = 32)
    private String source;

    @Id
    @Column(name = "external_id", nullable = false, length = 128)
    private String externalId;

    @Column(name = "rank_order", nullable = false)
    private Integer rankOrder;

    @Column(name = "title", nullable = false, length = 512)
    private String title;

    @Column(name = "external_url", nullable = false, length = 1024)
    private String externalUrl;

    @Column(name = "image_url", nullable = false, length = 1024)
    private String imageUrl;

    @Column(name = "price")
    private Integer price;

    @Column(name = "mall_name", length = 255)
    private String mallName;

    @Column(name = "similarity_score", nullable = false, precision = 4, scale = 3)
    private BigDecimal similarityScore;

    @Column(name = "fetched_at", nullable = false)
    private LocalDateTime fetchedAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    // ---- accessors -------------------------------------------------------
    public String getFurnitureId() { return furnitureId; }
    public void   setFurnitureId(String v) { this.furnitureId = v; }
    public String getSource() { return source; }
    public void   setSource(String v) { this.source = v; }
    public String getExternalId() { return externalId; }
    public void   setExternalId(String v) { this.externalId = v; }
    public Integer getRankOrder() { return rankOrder; }
    public void    setRankOrder(Integer v) { this.rankOrder = v; }
    public String getTitle() { return title; }
    public void   setTitle(String v) { this.title = v; }
    public String getExternalUrl() { return externalUrl; }
    public void   setExternalUrl(String v) { this.externalUrl = v; }
    public String getImageUrl() { return imageUrl; }
    public void   setImageUrl(String v) { this.imageUrl = v; }
    public Integer getPrice() { return price; }
    public void    setPrice(Integer v) { this.price = v; }
    public String getMallName() { return mallName; }
    public void   setMallName(String v) { this.mallName = v; }
    public BigDecimal getSimilarityScore() { return similarityScore; }
    public void       setSimilarityScore(BigDecimal v) { this.similarityScore = v; }
    public LocalDateTime getFetchedAt() { return fetchedAt; }
    public void          setFetchedAt(LocalDateTime v) { this.fetchedAt = v; }
    public LocalDateTime getExpiresAt() { return expiresAt; }
    public void          setExpiresAt(LocalDateTime v) { this.expiresAt = v; }

    /** Composite primary key — required by {@link IdClass}. */
    public static class PK implements Serializable {
        private String furnitureId;
        private String source;
        private String externalId;

        public PK() {}
        public PK(String furnitureId, String source, String externalId) {
            this.furnitureId = furnitureId;
            this.source = source;
            this.externalId = externalId;
        }
        @Override
        public boolean equals(Object o) {
            if (!(o instanceof PK p)) return false;
            return Objects.equals(furnitureId, p.furnitureId)
                && Objects.equals(source, p.source)
                && Objects.equals(externalId, p.externalId);
        }
        @Override
        public int hashCode() {
            return Objects.hash(furnitureId, source, externalId);
        }
    }
}

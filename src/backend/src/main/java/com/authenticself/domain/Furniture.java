package com.authenticself.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * JPA mapping for the {@code furniture} table (PRD §3, §7).
 * <p>
 * Task 1 seeded only the four identity columns ({@code furnitureId},
 * {@code type}, {@code style}, {@code size}). UC-01-recommendation
 * (Task 5) V4 migration adds the eight columns required by the scorer:
 * {@code name}, {@code price}, {@code imageUrl}, {@code colorHex},
 * {@code widthCm}, {@code lengthCm}, {@code heightCm}, {@code styleTags}.
 *
 * <p>{@code styleTags} is stored as a simple comma-separated string
 * (e.g. {@code "MODERN,SCANDINAVIAN"}). The {@link #getStyleTagsList()}
 * helper splits on commas and trims whitespace.
 */
@Entity
@Table(name = "furniture")
public class Furniture {

    @Id
    @Column(name = "furniture_id", nullable = false, length = 64)
    private String furnitureId;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "type", nullable = false, length = 32)
    private String type;

    @Column(name = "style", nullable = false, length = 64)
    private String style;

    @Column(name = "size", nullable = false, length = 100)
    private String size;

    @Column(name = "price", nullable = false)
    private Integer price;

    @Column(name = "image_url", length = 512)
    private String imageUrl;

    @Column(name = "color_hex", nullable = false, length = 7)
    private String colorHex;

    @Column(name = "width_cm", nullable = false)
    private Integer widthCm;

    @Column(name = "length_cm", nullable = false)
    private Integer lengthCm;

    @Column(name = "height_cm", nullable = false)
    private Integer heightCm;

    @Column(name = "style_tags", nullable = false, length = 255)
    private String styleTags;

    // ---------- accessors ------------------------------------------------
    public String getFurnitureId()           { return furnitureId; }
    public void   setFurnitureId(String v)   { this.furnitureId = v; }

    public String getName()           { return name; }
    public void   setName(String v)   { this.name = v; }

    public String getType()           { return type; }
    public void   setType(String v)   { this.type = v; }

    public String getStyle()           { return style; }
    public void   setStyle(String v)   { this.style = v; }

    public String getSize()           { return size; }
    public void   setSize(String v)   { this.size = v; }

    public Integer getPrice()           { return price; }
    public void    setPrice(Integer v)  { this.price = v; }

    public String getImageUrl()           { return imageUrl; }
    public void   setImageUrl(String v)   { this.imageUrl = v; }

    public String getColorHex()           { return colorHex; }
    public void   setColorHex(String v)   { this.colorHex = v; }

    public Integer getWidthCm()          { return widthCm; }
    public void    setWidthCm(Integer v) { this.widthCm = v; }

    public Integer getLengthCm()          { return lengthCm; }
    public void    setLengthCm(Integer v) { this.lengthCm = v; }

    public Integer getHeightCm()          { return heightCm; }
    public void    setHeightCm(Integer v) { this.heightCm = v; }

    public String getStyleTags()          { return styleTags; }
    public void   setStyleTags(String v)  { this.styleTags = v; }

    /**
     * Split {@link #styleTags} on {@code ,} and trim whitespace (AC-42).
     * Returns an immutable list — callers that mutate tags must persist
     * the full re-joined string via {@link #setStyleTags(String)}.
     */
    public List<String> getStyleTagsList() {
        if (styleTags == null || styleTags.isBlank()) {
            return Collections.emptyList();
        }
        List<String> out = new ArrayList<>();
        for (String raw : styleTags.split(",")) {
            String t = raw.trim();
            if (!t.isEmpty()) {
                out.add(t);
            }
        }
        return Collections.unmodifiableList(out);
    }
}

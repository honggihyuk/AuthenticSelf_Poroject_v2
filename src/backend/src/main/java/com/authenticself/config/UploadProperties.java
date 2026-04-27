package com.authenticself.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Type-safe binding for the {@code app.upload.*} block in {@code application.yml}
 * (FR-8 / AC-21). All values env-overridable.
 */
@ConfigurationProperties(prefix = "app.upload")
public class UploadProperties {

    /** Max raw byte length accepted at the endpoint. Default 10 MB. */
    private long maxSizeBytes = 10L * 1024 * 1024;

    /** Lowest accepted decoded width, pixels. */
    private int minWidth = 640;

    /** Lowest accepted decoded height, pixels. */
    private int minHeight = 480;

    /** Allow-list of acceptable MIME types (lower-case). */
    private List<String> allowedContentTypes =
            List.of("image/jpeg", "image/png", "image/webp");

    public long getMaxSizeBytes()           { return maxSizeBytes; }
    public void setMaxSizeBytes(long v)     { this.maxSizeBytes = v; }

    public int  getMinWidth()               { return minWidth; }
    public void setMinWidth(int v)          { this.minWidth = v; }

    public int  getMinHeight()              { return minHeight; }
    public void setMinHeight(int v)         { this.minHeight = v; }

    public List<String> getAllowedContentTypes()       { return allowedContentTypes; }
    public void setAllowedContentTypes(List<String> v) { this.allowedContentTypes = v; }
}

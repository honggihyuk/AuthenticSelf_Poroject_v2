package com.authenticself.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Type-safe binding for the {@code app.storage.*} block (FR-8 / AC-21).
 */
@ConfigurationProperties(prefix = "app.storage")
public class StorageProperties {

    private final Local local = new Local();

    public Local getLocal() { return local; }

    public static class Local {
        /** Root directory for the local-FS object-storage impl. */
        private String root = "./var/object-storage";

        public String getRoot()        { return root; }
        public void   setRoot(String v) { this.root = v; }
    }
}

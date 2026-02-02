package net.blueva.menu.sync;

import java.time.Instant;

public record MenuMetadata(String menuKey, long version, Instant updatedAt, String fileName) {
    // Constructor for single menu metadata (without menuKey for backward compatibility)
    public MenuMetadata(long version, Instant updatedAt, String fileName) {
        this(null, version, updatedAt, fileName);
    }
}

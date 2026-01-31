package net.blueva.menu.sync;

import java.time.Instant;

public record MenuMetadata(long version, Instant updatedAt, String fileName) {
}

package net.blueva.menu.sync;

import java.time.Instant;

public record MenuRecord(MenuType type, String menuKey, String fileName, String yaml, long version, Instant updatedAt) {
}

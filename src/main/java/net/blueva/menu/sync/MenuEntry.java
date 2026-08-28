package net.blueva.menu.sync;

public record MenuEntry(MenuType type, String menuKey, String fileName, String rawEntry) {
    public String normalizedEntry() {
        return menuKey + ";" + fileName;
    }
}

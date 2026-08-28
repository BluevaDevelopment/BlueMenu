package net.blueva.menu.sync;

public enum MenuType {
    JAVA("java"),
    BEDROCK("bedrock");

    private final String configKey;

    MenuType(String configKey) {
        this.configKey = configKey;
    }

    public String getConfigKey() {
        return configKey;
    }
}

package net.blueva.menu.sync;

public record MySqlConfig(
    String host,
    int port,
    String database,
    String username,
    String password,
    boolean useSsl,
    int poolSize,
    long connectionTimeoutMs,
    long maxLifetimeMs
) {
}

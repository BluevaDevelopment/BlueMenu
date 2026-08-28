package net.blueva.menu.sync;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public class MySqlMenuRepository implements MenuRepository {
    private static final String TABLE_NAME = "bluemenu_menus";
    private static final String CREATE_TABLE_SQL = "CREATE TABLE IF NOT EXISTS " + TABLE_NAME + " ("
        + "menu_type VARCHAR(16) NOT NULL,"
        + "menu_key VARCHAR(64) NOT NULL,"
        + "file_name VARCHAR(128) NOT NULL,"
        + "yaml LONGTEXT NOT NULL,"
        + "version BIGINT NOT NULL DEFAULT 1,"
        + "updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,"
        + "PRIMARY KEY (menu_type, menu_key),"
        + "INDEX idx_updated_at (updated_at)"
        + ") DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci";

    private final HikariDataSource dataSource;

    public MySqlMenuRepository(MySqlConfig config) {
        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setJdbcUrl(buildJdbcUrl(config));
        hikariConfig.setUsername(config.username());
        hikariConfig.setPassword(config.password());
        hikariConfig.setMaximumPoolSize(config.poolSize());
        hikariConfig.setConnectionTimeout(config.connectionTimeoutMs());
        hikariConfig.setMaxLifetime(config.maxLifetimeMs());
        hikariConfig.setPoolName("BlueMenu-MySQL");
        hikariConfig.setAutoCommit(true);
        this.dataSource = new HikariDataSource(hikariConfig);
    }

    @Override
    public void ensureSchema() throws SQLException {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.executeUpdate(CREATE_TABLE_SQL);
        }
    }

    @Override
    public Optional<MenuRecord> fetchMenu(MenuType type, String menuKey) throws SQLException {
        String sql = "SELECT menu_type, menu_key, file_name, yaml, version, updated_at FROM " + TABLE_NAME
            + " WHERE menu_type = ? AND menu_key = ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, type.getConfigKey());
            statement.setString(2, menuKey);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return Optional.of(mapRecord(resultSet));
                }
            }
        }
        return Optional.empty();
    }

    @Override
    public Optional<MenuMetadata> fetchMetadata(MenuType type, String menuKey) throws SQLException {
        String sql = "SELECT version, updated_at, file_name FROM " + TABLE_NAME
            + " WHERE menu_type = ? AND menu_key = ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, type.getConfigKey());
            statement.setString(2, menuKey);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    long version = resultSet.getLong("version");
                    Timestamp updatedAt = resultSet.getTimestamp("updated_at");
                    String fileName = resultSet.getString("file_name");
                    return Optional.of(new MenuMetadata(version, toInstant(updatedAt), fileName));
                }
            }
        }
        return Optional.empty();
    }

    @Override
    public List<MenuMetadata> fetchAllMetadata(MenuType type) throws SQLException {
        String sql = "SELECT menu_key, version, updated_at, file_name FROM " + TABLE_NAME
            + " WHERE menu_type = ?";
        java.util.List<MenuMetadata> results = new java.util.ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, type.getConfigKey());
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    String menuKey = resultSet.getString("menu_key");
                    long version = resultSet.getLong("version");
                    Timestamp updatedAt = resultSet.getTimestamp("updated_at");
                    String fileName = resultSet.getString("file_name");
                    results.add(new MenuMetadata(menuKey, version, toInstant(updatedAt), fileName));
                }
            }
        }
        return results;
    }

    @Override
    public long insertMenu(MenuType type, String menuKey, String fileName, String yaml) throws SQLException {
        String sql = "INSERT INTO " + TABLE_NAME + " (menu_type, menu_key, file_name, yaml, version)"
            + " VALUES (?, ?, ?, ?, 1)";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, type.getConfigKey());
            statement.setString(2, menuKey);
            statement.setString(3, fileName);
            statement.setString(4, yaml);
            statement.executeUpdate();
        }
        return 1L;
    }

    @Override
    public long updateMenu(MenuType type, String menuKey, String fileName, String yaml) throws SQLException {
        String sql = "UPDATE " + TABLE_NAME + " SET yaml = ?, file_name = ?, version = version + 1"
            + " WHERE menu_type = ? AND menu_key = ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, yaml);
            statement.setString(2, fileName);
            statement.setString(3, type.getConfigKey());
            statement.setString(4, menuKey);
            statement.executeUpdate();
        }
        return fetchMetadata(type, menuKey).map(MenuMetadata::version).orElse(1L);
    }

    @Override
    public void close() {
        if (dataSource != null) {
            dataSource.close();
        }
    }

    private MenuRecord mapRecord(ResultSet resultSet) throws SQLException {
        String menuType = resultSet.getString("menu_type");
        String menuKey = resultSet.getString("menu_key");
        String fileName = resultSet.getString("file_name");
        String yaml = resultSet.getString("yaml");
        long version = resultSet.getLong("version");
        Timestamp updatedAt = resultSet.getTimestamp("updated_at");
        return new MenuRecord(MenuType.valueOf(menuType.toUpperCase()), menuKey, fileName, yaml, version, toInstant(updatedAt));
    }

    private Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? Instant.EPOCH : timestamp.toInstant();
    }

    private String buildJdbcUrl(MySqlConfig config) {
        String sslFlag = config.useSsl() ? "true" : "false";
        return "jdbc:mysql://" + config.host() + ":" + config.port() + "/" + config.database()
            + "?useSSL=" + sslFlag
            + "&characterEncoding=UTF-8"
            + "&useUnicode=true"
            + "&connectionCollation=utf8mb4_unicode_ci";
    }
}

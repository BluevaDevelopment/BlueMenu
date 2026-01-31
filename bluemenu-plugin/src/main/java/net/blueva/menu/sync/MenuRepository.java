package net.blueva.menu.sync;

import java.sql.SQLException;
import java.util.Optional;

public interface MenuRepository {
    void ensureSchema() throws SQLException;

    Optional<MenuRecord> fetchMenu(MenuType type, String menuKey) throws SQLException;

    Optional<MenuMetadata> fetchMetadata(MenuType type, String menuKey) throws SQLException;

    long insertMenu(MenuType type, String menuKey, String fileName, String yaml) throws SQLException;

    long updateMenu(MenuType type, String menuKey, String fileName, String yaml) throws SQLException;

    void close();
}

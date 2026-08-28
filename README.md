<p align="center">
  <img src="docs/assets/bluemenu-logo.png" alt="BlueMenu" width="760">
</p>

<p align="center">
  <strong>GUI menus for Spigot/Paper that serve Java and Bedrock players from one plugin.</strong>
</p>

<p align="center">
  <img alt="Version" src="https://img.shields.io/badge/version-1.5.0-blue">
  <img alt="Java" src="https://img.shields.io/badge/Java-21-ED8B00?logo=openjdk&logoColor=white">
  <img alt="Build" src="https://img.shields.io/badge/build-Maven-C71A36?logo=apachemaven&logoColor=white">
  <img alt="Minecraft" src="https://img.shields.io/badge/Minecraft-1.21+-62B47A">
  <img alt="Bedrock" src="https://img.shields.io/badge/Bedrock-Floodgate%2FGeyser-6EE7B7">
  <img alt="License" src="https://img.shields.io/badge/license-GPL--3.0-green">
</p>

## Overview

BlueMenu builds interactive menus for Minecraft servers: navigation hubs, shops, kit selectors, warp lists and anything else driven by clickable items. The point of difference is cross-platform reach. Java players get chest inventories, and Bedrock players connected through GeyserMC and Floodgate get native forms, both reachable under the same menu name, so a server running both does not need two plugins to keep aligned.

Each platform keeps its own menu file, registered in `settings.yml` under a shared name, which leaves the Bedrock form free to use components that have no inventory equivalent. `/bm open auto <menu>` then routes every player to the version their client can actually render.

## Features

- **Native UI on both platforms.** Java players get chest inventories; Bedrock players get native MODAL, SIMPLE or CUSTOM forms. Bedrock support stays dormant when Floodgate is absent, and `auto` falls back to the Java menu.
- **Conditional visibility** through `display_conditions`, with comparison operators, boolean logic and parentheses, evaluated per player.
- **Actions per click type**: `MESSAGE`, `BROADCAST`, `CONSOLE`, `PLAYER`, `SOUND`, `CLOSE`, `OPEN_MENU`, `REFRESH_MENU`, `CONNECT`, `CONNECT_BUNGEE` and `CONNECT_VELOCITY`.
- **Granular click handling**: `LEFT_CLICK`, `RIGHT_CLICK`, `SHIFT_LEFT_CLICK`, `SHIFT_RIGHT_CLICK`, `MIDDLE_CLICK`, plus the `BOTH`, `BOTH_SHIFT` and `ALL` shorthands.
- **Custom item integrations** with ItemsAdder, Oraxen and Nexo, resolved through a provider layer that stays inactive when none of them is installed.
- **Player heads** by player name or base64 texture, with animation support.
- **MiniMessage formatting** via Adventure, including gradients and hex colors.
- **PlaceholderAPI support** anywhere text is rendered.
- **Cross-server menu sync** over MySQL, with wildcard send and receive lists, a configurable poll interval and a conflict policy.
- **Web editor** reachable with `/bm editor`, connected over WebSocket with an in-game confirmation flow and per-window verification tokens.
- **DeluxeMenus converter** through `/bm convert`, covering items, enchantments, item flags, view requirements, priorities and per-click commands.

## Project Layout

```
src/main/java/net/blueva/menu/
├── commands/        command handler, subcommands and tab completion
├── common/          session DTOs and message types of the web editor protocol
├── configuration/   settings and menu loading, backed by BoostedYAML
├── libraries/       bundled bStats metrics
├── listeners/       inventory and player event handling
├── managers/        condition evaluation, plus java/ and bedrock/ renderers
├── sync/            MySQL menu synchronization
├── utils/           text, color and item helpers
└── webeditor/       WebSocket client for the web editor
```

## Requirements

| Component | Version |
|---|---|
| Java | 21+ |
| Server | Spigot/Paper 1.21+ |
| Optional | [GeyserMC](https://geysermc.org/) with [Floodgate](https://geysermc.org/wiki/floodgate/) for Bedrock menus |
| Optional | [PlaceholderAPI](https://www.spigotmc.org/resources/placeholderapi.6245/) |
| Optional | ItemsAdder, Oraxen or Nexo for custom items |
| Optional | MySQL 8+ for cross-server menu sync |

## Commands

All commands live under `/bluemenu`, aliased as `/bm`, `/menu` and `/menus`.

| Command | Permission | Description |
|---|---|---|
| `/bm help` | `bluemenu.help` | List available commands |
| `/bm list` | `bluemenu.list` | List loaded Java and Bedrock menus |
| `/bm open <java\|bedrock\|auto> <menu> [player]` | `bluemenu.open` | Open a menu, `bluemenu.open.others` to target another player |
| `/bm reload` | `bluemenu.reload` | Reload settings and menu files |
| `/bm editor` | `bluemenu.editor` | Start a web editor session |
| `/bm confirm` | `bluemenu.editor` | Confirm a pending web editor session |
| `/bm convert` | `bluemenu.convert` | Convert DeluxeMenus menus to BlueMenu format |

## Configuration

`settings.yml` holds the plugin-wide options: metrics, web editor behaviour, the registered Java and Bedrock menu lists, and the `sync-menus` block for MySQL synchronization. Menu files live under `menus/java/` and `menus/bedrock/`, and the plugin ships working examples for every supported form type.

## Building

```bash
mvn clean package
```

The JAR is produced at `target/BlueMenu-1.5.0.jar`.

## Authors

- Blueva
- Whiron
- Arthuurrr

Website: [blueva.net](https://blueva.net)

## License

Licensed under the [GNU General Public License v3.0](LICENSE).

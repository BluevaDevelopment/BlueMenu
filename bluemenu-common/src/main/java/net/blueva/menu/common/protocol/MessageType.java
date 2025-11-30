package net.blueva.menu.common.protocol;

/**
 * Types of messages exchanged via WebSocket
 */
public enum MessageType {
    // Plugin -> WebServer
    SESSION_CREATE,
    SESSION_CONFIRM,
    MENU_UPDATE,
    MENU_LIST,
    MENU_SAVED,
    MENU_DELETED,

    // WebServer -> Plugin
    SESSION_VALIDATE,
    SESSION_CONFIRMED,
    MENU_LIST_REQUEST,
    MENU_GET,
    MENU_SAVE,
    MENU_CREATE,
    MENU_DELETE,

    // WebServer -> Web Panel
    SESSION_VALID,
    SESSION_INVALID,
    MENU_DATA,

    // Generic
    PING,
    PONG,
    ERROR
}

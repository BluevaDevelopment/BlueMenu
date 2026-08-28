package net.blueva.menu.common.dto;

/**
 * Represents basic metadata of a menu for the web editor
 */
public class MenuMetadataDTO {
    private String fileName;       // e.g., "chest_example.yml"
    private String menuName;       // Display name from config
    private String platform;       // "JAVA" or "BEDROCK"
    private String type;           // "CHEST", "SIMPLE", "MODAL", "CUSTOM"
    private String openCommand;    // Command to open the menu
    private int itemCount;         // Number of items/buttons (optional)

    // Default constructor for JSON serialization
    public MenuMetadataDTO() {
    }

    public MenuMetadataDTO(String fileName, String menuName, String platform, String type, String openCommand, int itemCount) {
        this.fileName = fileName;
        this.menuName = menuName;
        this.platform = platform;
        this.type = type;
        this.openCommand = openCommand;
        this.itemCount = itemCount;
    }

    // Getters and setters
    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getMenuName() {
        return menuName;
    }

    public void setMenuName(String menuName) {
        this.menuName = menuName;
    }

    public String getPlatform() {
        return platform;
    }

    public void setPlatform(String platform) {
        this.platform = platform;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getOpenCommand() {
        return openCommand;
    }

    public void setOpenCommand(String openCommand) {
        this.openCommand = openCommand;
    }

    public int getItemCount() {
        return itemCount;
    }

    public void setItemCount(int itemCount) {
        this.itemCount = itemCount;
    }
}

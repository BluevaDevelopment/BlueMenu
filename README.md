# BlueMenu

BlueMenu is a GUI (Graphical User Interface) plugin for Spigot 1.20+ and GeyserMC/Floodgate

⚠️ **Warning: This plugin is still a work in progress and is not recommended for use on public servers.** ⚠️

## Description

BlueMenu is a plugin that adds a visual user interface to your Minecraft server. It allows you to create custom menus with interactive items and associated actions. You can use it to create navigation menus, shops, kit selection menus, and more. 

This plugin has been created in order to be able to create menus both in Java and Bedrock (for servers using GeyserMC with Floodgate) in the latest version of Spigot. 

## Requirements
- Spigot 1.20+
- (Optional) GeyserMC with Floodgate

## Compiling from Source
To build the plugin from source, you will need to have [Java Development Kit (JDK) 17](https://adoptium.net/) installed on your machine. 

With an IDE like IntelliJ IDEA or Eclipse, you can easily import the source code and build the plugin using Maven. Here are the steps to build Blue Menu using IntelliJ IDEA:

1. Download and install IntelliJ IDEA on your computer.
2. Clone the Blue Menu repository to your local machine.
3. Open IntelliJ IDEA and click on "Open" or "Import Project".
4. Navigate to the location where you cloned the Blue Menu repository.
5. Choose "Import project from external model" and then "Maven".
6. Follow the instructions on the screen and configure any additional options as needed.
7. Click on "Finish" to import the project.
8. Once imported, open the "pom.xml" file to verify that all dependencies are resolved correctly.
9. Compile the project by clicking on "Build".

## Installation
1. Compile the plugin code with Maven following the previous guide.
2. Place the JAR file in the "plugins" folder of your Minecraft server.
3. Restart the server.
4. Configure the menus and actions in the plugin configuration files.

## Usage
1. Edit the plugin's configuration file to define the menus and items.
2. Restart the server or use the `/bluemenu reload` command to load the changes.
3. Use the `/bluemenu open [java/bedrock/auto] [menu] (player)` command to open a menu.
4. Interact with the menu items to perform the associated actions.

## Support Blue Menu
Blue Menu is maintained by developers in their spare time. If you enjoy using Blue Menu and would like to support its development, consider [making a donation](https://ko-fi.com/V7V3IE7VS). Your support will help us continue to improve and maintain this project.

[![ko-fi](https://ko-fi.com/img/githubbutton_sm.svg)](https://ko-fi.com/V7V3IE7VS)
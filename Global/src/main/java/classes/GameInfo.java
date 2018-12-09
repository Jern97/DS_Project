package classes;

import java.io.Serializable;

public class GameInfo implements Serializable {

    String hostName;
    String name;
    boolean started;
    String id;
    int width;
    int height;
    int maxPlayers;
    int numberOfPlayersJoined;
    int theme_id;

    public GameInfo() {
    }

    public GameInfo(String hostName, String name, String id, int width, int height, int maxPlayers, int numberOfPlayersJoined, boolean started, int theme_id) {
        this.hostName = hostName;
        this.name = name;
        this.id = id;
        this.width = width;
        this.height = height;
        this.maxPlayers = maxPlayers;
        this.numberOfPlayersJoined = numberOfPlayersJoined;
        this.theme_id = theme_id;
    }

    public String getHostName() {
        return hostName;
    }

    public void setHostName(String hostName) {
        this.hostName = hostName;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public boolean isStarted() {
        return started;
    }

    public void setStarted(boolean started) {
        this.started = started;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public int getWidth() {
        return width;
    }

    public void setWidth(int width) {
        this.width = width;
    }

    public int getHeight() {
        return height;
    }

    public void setHeight(int height) {
        this.height = height;
    }

    public int getMaxPlayers() {
        return maxPlayers;
    }

    public void setMaxPlayers(int maxPlayers) {
        this.maxPlayers = maxPlayers;
    }

    public int getNumberOfPlayersJoined() {
        return numberOfPlayersJoined;
    }

    public void setNumberOfPlayersJoined(int numberOfPlayersJoined) {
        this.numberOfPlayersJoined = numberOfPlayersJoined;
    }

    public int getTheme_id() {
        return theme_id;
    }

    public void setTheme_id(int theme_id) {
        this.theme_id = theme_id;
    }
}

package applicationserver;

import classes.Coordinate;
import classes.Field;
import classes.GameInfo;
import classes.PlayerInfo;
import exceptions.*;
import interfaces.ClientInterface;
import interfaces.GameInterface;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.*;

public class Game extends UnicastRemoteObject implements GameInterface {
    private String id;
    private String name;
    private Map<Coordinate, Field> board;
    private ArrayList<Player> allPlayers = new ArrayList<>();
    private Queue<Player> playerQueue = new LinkedList<>();
    private Set<Player> spectators;
    private int theme_id = 0;
    private int max_players;
    private int x_size;
    private int y_size;
    private Player current_player;
    private boolean started = false;
    private Thread gameThread;
    private Lobby lobby;
    private boolean backup;
    private GameInterface backupGame;

    public Game(String name, int x_size, int y_size, int max_players, String id, ClientInterface client, Lobby lobby, int theme_id, boolean backup) throws InvalidSizeException, RemoteException, InvalidCredentialsException, AlreadyPresentException, ThemeNotLargeEnoughException {
        this.name = name;
        this.x_size = x_size;
        this.y_size = y_size;
        this.id = id;
        this.max_players = max_players;
        this.lobby = lobby;
        this.theme_id = theme_id;
        this.backup = backup;
        //checken als het board een even aantal veldjes heeft
        board = new HashMap<>();
        if ((x_size * y_size) % 2 != 0) {
            throw new InvalidSizeException("Number of fields on board must be even");
        }
        //Elke combinatie 2x toevoegen aan een lijst;
        List<Integer> combinations = new LinkedList<>();
        for (int i = 0; i < (x_size * y_size) / 2; i++) {
            combinations.add(i);
            combinations.add(i);
        }
        //Deze lijst gebruiken om de velden op het spelbord op te vullen
        for (int x = 0; x < x_size; x++) {
            for (int y = 0; y < y_size; y++) {
                int list_index = (int) Math.floor(Math.random() * combinations.size());
                board.put(new Coordinate(x, y), new Field(x, y, combinations.get(list_index)));
                combinations.remove(list_index);
            }
        }

        //Als de huidige game geen backup is dan moeten we een backup game creeeren op de backup server
        if (!backup){
            backupGame = ApplicationServer.getInstance().getBackupServer().getLobby().makeNewGame(id, name, x_size, y_size, max_players, client, theme_id, true);
        }

    }

    public void addPlayer(ClientInterface newClient) throws AlreadyPresentException {
        try {

            System.out.println("INFO: new player added: " + newClient.getUsername());
            Player newPlayer = new Player(newClient, newClient.getUsername());
            if (allPlayers.contains(newPlayer)) throw new AlreadyPresentException();

            playerQueue.add(newPlayer);
            allPlayers.add(newPlayer);
            pushPlayerlist();
            Lobby.getInstance().getDb().updateGameInfo(this.getGameInfo());

        } catch (RemoteException e) {
            e.printStackTrace();
        }

    }

    public void addSpectator(ClientInterface newClient) {
        try {
            System.out.println("INFO: new spectator joined: " + newClient.getUsername());
            Player newPlayer = new Player(newClient, newClient.getUsername());
            allPlayers.add(newPlayer);
        } catch (RemoteException re) {
            re.printStackTrace();
        }
    }

    public void leaveGame(ClientInterface client) throws RemoteException {
        Player toDelete = null;
        for (Player p : allPlayers) {
            if (p.getGameclient().isSameClient(client)) {
                toDelete = p;
            }
        }

        if (toDelete != null) {
            allPlayers.remove(toDelete);
            playerQueue.remove(toDelete);
        }

        Lobby.getInstance().getDb().updateGameInfo(this.getGameInfo());
        pushPlayerlist();

        if (allPlayers.isEmpty()){
            try {
                ApplicationServer.getInstance().getBackupServer().getLobby().terminateGame(backupGame);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
            try {
                lobby.terminateGame(this);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
    }

    public synchronized void readyUp(ClientInterface client) throws RemoteException {
        //com.kuleuven.distributedsystems.applicationserver.Player op ready zetten
        for (Player p : playerQueue) {
            if (p.getGameclient().isSameClient(client)) p.setReady(true);
        }

        //Checken als alle spelers klaar zijn
        boolean allPlayersReady = true;
        for (Player p : playerQueue) {
            if (!p.isReady()) {
                allPlayersReady = false;
                break;
            }
        }
        //com.kuleuven.distributedsystems.applicationserver.Game starten als alle spelers klaar zijn
        if (allPlayersReady) startGame();
        pushPlayerlist();

    }

    private void startGame() {
        //TODO een deftige thread hiervoor schrijven
        System.out.println("Starting game " + id);
        started = true;
        //Spelers laten weten dat de game begonnen is
        for (Player p : allPlayers) {
            try {
                p.getGameclient().setGameStarted();
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }

        //Gamethread opstarten om moves te vragen
        gameThread = new Thread(this::runGame);
        gameThread.start();
    }

    private void runGame() {
        //Zolang er nog spelers meedoen, en niet alle velden zijn omgedraait spelen we verder
        while (!playerQueue.isEmpty() && !allFieldsFlipped()) {
            try {
                requestMove();
            } catch (LeftGameException lge) {
                try {
                    leaveGame(current_player.getGameclient());
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }
        }
        //com.kuleuven.distributedsystems.applicationserver.Game uit de live_games list halen
        pushInfoLabel("Game finished");
        try {
            ApplicationServer.getInstance().getBackupServer().getLobby().terminateGame(backupGame);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
        try {
            lobby.terminateGame(this);
        } catch (RemoteException e) {
            e.printStackTrace();
        }

    }

    private void requestMove() throws LeftGameException {
        try {//1ste speler in queue opzoeken
            current_player = playerQueue.peek();

            //We requesten 2 moves van deze client
            Coordinate c_one = current_player.getGameclient().requestMove();
            Field moveOne = board.get(c_one);
            //Push de 1ste tile door naar alle clients
            pushShowTile(c_one);

            Coordinate c_two = current_player.getGameclient().requestMove();
            Field moveTwo = board.get(c_two);
            //Push de 2de tile door naar alle clients
            pushShowTile(c_two);

            //Move wordt gemaakt, er wordt gekeken als het een goeie move is
            if (makeMove(moveOne, moveTwo)) {
                //Goeie move, deze speler blijft aan de beurt
                pushPlayerlist();
            } else {
                //Slechte move, we halen de speler van de queue en steken hem terug vanachter
                playerQueue.poll();
                playerQueue.add(current_player);
                //Tiles terug verbergen
                pushHideTile(c_one);
                pushHideTile(c_two);
            }
        } catch (RemoteException re) {
            re.printStackTrace();
        }

    }

    private synchronized boolean makeMove(Field one, Field two) {
        if (one.isFlipped() || two.isFlipped()) {
            return false;
        }
        boolean point = false;

        if (one.getValue() == two.getValue()) {
            //Valid Move
            one.setFlipped(true);
            two.setFlipped(true);
            current_player.addPoint();
            point = true;
        }
        return point;
    }

    public void pushPlayerlist() {

        //pushen naar client
        List<PlayerInfo> playerInfoList = new ArrayList<>();
        for (Player p : playerQueue) {
            playerInfoList.add(p.convertToPlayerInfo());
        }
        for (Player p : allPlayers) {
            try {
                p.getGameclient().updatePlayerInfo(playerInfoList);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
    }

    public void pushInfoLabel(String s) {
        //pushen naar client
        for (Player p : allPlayers) {
            try {
                p.getGameclient().updateInfoLabel(s);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
    }

    public List<PlayerInfo> getPlayerlist() {
        List<PlayerInfo> playerInfoList = new ArrayList<>();
        for (Player p : playerQueue) {
            playerInfoList.add(p.convertToPlayerInfo());
        }
        return playerInfoList;
    }

    public HashMap<Coordinate, Integer> getFlippedFields() {
        HashMap<Coordinate, Integer> toReturn = new HashMap<>();
        for (Coordinate c : board.keySet()) {
            if (board.get(c).isFlipped()) {
                toReturn.put(c, board.get(c).getValue());
            }
        }
        return toReturn;
    }

    public int getThemeId() {
        return theme_id;
    }

    /**
     * Deze methode pusht een bepaalde move van een speler naar alle clients;
     */

    public void pushShowTile(Coordinate c) {
        for (Player p : allPlayers) {
            try {
                p.getGameclient().showTile(c, board.get(c).getValue());
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
    }

    public void pushHideTile(Coordinate c) {
        for (Player p : allPlayers) {
            try {
                p.getGameclient().hideTile(c, 1000);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
    }

    //TODO: methode schrijven om flips te pushen

    @Override
    public int getValueOf(Coordinate coordinate){
        return board.get(coordinate).getValue();
    }


    @Override
    public String getId() {
        return id;
    }

    public Queue<Player> getPlayerQueue() {
        return playerQueue;
    }

    @Override
    public int getMax_players() {
        return max_players;
    }

    public boolean isStarted() {
        return started;
    }

    public int getWidth() {
        return x_size;
    }

    public int getHeight() {
        return y_size;
    }

    public GameInfo getGameInfo() throws RemoteException {
        return new GameInfo(lobby.getName(), name, id, x_size, y_size, max_players, playerQueue.size(), started, theme_id);
    }

    public boolean allFieldsFlipped() {
        for (Field f : board.values()) {
            if (!f.isFlipped()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public String toString() {
        return "Game{" +
                "id='" + id + '\'' +
                '}';
    }
}

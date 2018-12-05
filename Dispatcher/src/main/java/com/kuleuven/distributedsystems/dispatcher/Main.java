package com.kuleuven.distributedsystems.dispatcher;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.io.IOException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/*
* Voor het distribueren van de App servers zullen we het volgende doen:
* Elke game op een bepaalde app server heeft een backup op een andere app server
* Wanneer zijn main App server weg valt schakelt hij over op de backup App server.
* De backup server wordt nu de main App server voor het spel.
* De backup app server contacteert de dispatcher.
* De dispatcher maakt een nieuwe App server aan die terug fungeert als backup app server voor dat spel.
* Elke backup App server kan dus ook gebruikt worden als Main app server voor andere games.
* */
@SpringBootApplication
public class Main {

    private static String serverName;
    private static String pathToJars;

    private static int port;
    private static int restPort;

    private static int dbPortCounter;
    private static int appPortCounter;
    private static int appRestPortCounter;

    private static final int DB_SERVER_COUNT = 0;
    private static final int dbPortOffset = 100;

    private static final int APP_SERVER_COUNT = 0;
    private static final int appPortOffset = 200;
    private static final int appRestPortOffset = 100;


    private static List<Runtime> runtimes = new ArrayList<>();


    public static void main(String[] args) {
        if (args.length != 0) {
            serverName = args[0];
            port = Integer.parseInt(args[1]);
            restPort = Integer.parseInt(args[2]);
            pathToJars = args[3];
        } else {
            serverName = "Dispatcher";
            port = 1000;
            restPort = 1500;
            pathToJars = "build/jars";
        }

        System.setProperty("server.port", String.valueOf(restPort));
        DispatcherImp dispatcherImp = startDispatcher(port);
        SpringApplication.run(Main.class, args);

        /*
        * Bij het opstarten van de dispatch worden 2 App servers aangemaakt en 4 database server
        * */

        Runtime rt1 = Runtime.getRuntime();

        try {
            for (int i = 0; i < DB_SERVER_COUNT; i++) {
                int serverCount = dbPortCounter;
                int newPort = port + dbPortOffset + dbPortCounter++;
                System.out.println("Starting new DB server with port " + newPort);
                rt1.exec("cmd /c start cmd.exe /K \"java -jar "+pathToJars+"/DatabaseServer-0.1.0.jar %cd%/DBServer"+serverCount+".db "+ newPort);
            }

            for (int i = 0; i < APP_SERVER_COUNT; i++) {
                int serverCount = appPortCounter;
                int newPort = port + appPortOffset + appPortCounter++;
                int newRestPort = restPort + appRestPortOffset + appRestPortCounter++;
                System.out.println("Starting new APP server with port " + newPort + " and API port " + newRestPort);
                rt1.exec("cmd /c start cmd.exe /K \"java -jar "+pathToJars+"/ApplicationServer-0.1.0.jar AppServer"+serverCount + " " + newPort + " " + newRestPort);
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static DispatcherImp startDispatcher(int port){
        try{
            DispatcherImp dispatcherImp = DispatcherImp.getInstance();
            Registry registry = LocateRegistry.createRegistry(port);
            registry.rebind("dispatcher_service", dispatcherImp);
            System.out.println("INFO: up and running on port: "+port);
            return dispatcherImp;
        }
        catch(RemoteException re){
            re.printStackTrace();
        }
        return null;
    }
}

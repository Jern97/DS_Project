package virtualclientserver.controllers;

import classes.ResponseMessage;
import exceptions.AlreadyPresentException;
import exceptions.InvalidCredentialsException;
import exceptions.UserNotLoggedInException;
import interfaces.ClientDispatcherInterface;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import virtualclientserver.Main;
import virtualclientserver.VirtualClient;
import virtualclientserver.VirtualClientServer;

import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

import static classes.ResponseType.NOK;
import static classes.ResponseType.OK;
import static constants.DispatcherConstants.DISPATCHER_CLIENT_PORT;
import static constants.DispatcherConstants.DISPATCHER_IP;
import static constants.ServiceConstants.CLIENT_DISPATCHER_SERVICE;

@RestController
@RequestMapping(value = "memory/appLogin")
public class AppLoginRestController {

    private static VirtualClientServer server = VirtualClientServer.getInstance();

    @RequestMapping(value = "login", produces = "application/json")
    public ResponseMessage login(@RequestParam String username, @RequestParam String token) {
        ResponseMessage responseMessage = null;

        try {

            VirtualClient client = new VirtualClient(username, token);

            Registry registry = LocateRegistry.getRegistry(DISPATCHER_IP, DISPATCHER_CLIENT_PORT);
            ClientDispatcherInterface dispatch = (ClientDispatcherInterface) registry.lookup(CLIENT_DISPATCHER_SERVICE);

            client.setDispatch(dispatch);
            client.connect();
            server.getConnectedClients().put(token, client);

            responseMessage = new ResponseMessage(OK, "Logged in.");
        } catch (AlreadyPresentException | InvalidCredentialsException e) {
            responseMessage = new ResponseMessage(NOK, e.getMessage());
        } catch (RemoteException | NotBoundException e) {
            responseMessage = new ResponseMessage(NOK, "A fatal error occurred.");
            e.printStackTrace();
        }

        return responseMessage;
    }

    @RequestMapping(value = "logout", produces = "application/json")
    public ResponseMessage logout(@RequestParam String token) {
        ResponseMessage responseMessage = null;

        try {
            VirtualClient client = ((VirtualClient) server.getClient(token));
            client.disconnect(true);
            server.getConnectedClients().remove(token);

            responseMessage = new ResponseMessage(OK, "Logged out.");
        } catch (UserNotLoggedInException e) {
            responseMessage = new ResponseMessage(NOK, e.getMessage());
        } catch (RemoteException e) {
            responseMessage = new ResponseMessage(NOK, "A fatal error occurred.");
            e.printStackTrace();
        }

        return responseMessage;
    }
}

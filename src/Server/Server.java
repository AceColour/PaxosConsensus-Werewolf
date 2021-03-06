package Server;

import Client.Communications.TCPRequestResponseChannel;
import Client.Paxos.ProposalId;
import GamePlay.*;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.io.*;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;

/**
 * Created by erickchandra on 4/25/16.
 */
public class Server extends Thread {
    private static int currentLastPlayerId = -1;
    private static Game game = new Game();
    private static ServerSocket serverSocket;
    private final Socket clientSocket;
    private String udpIpAddress;
    private int udpPortNumber;
    private int playerId;
    private static int voteDayNotDecidedCount = 0;
    public String username;
    private boolean aliveStatus = true;

    private static List<Server> serverList;

    private boolean leaveStatus = false;

    private TCPRequestResponseChannel communicator;

    public Server(Socket _clientSocket) {
        this.clientSocket = _clientSocket;
        this.communicator = new TCPRequestResponseChannel(this.clientSocket);
        start();
        this.communicator.start();
    }

    public Socket getClientSocket() {
        return clientSocket;
    }

    public int receiveJoin(String _ipAddr, int _portNo, String _username) {
        // receiveJoin will return integer >= 0 if the client successfully inserted become a player.
        // Returns -1 if username exists.
        // Returns -2 if game is running.

        if (game.isUsernameExists(_username)) {
            return -1;
        }
        else if (game.getStartedStatus()) {
            return -2;
        }
        else {
            game.clientConnect(new Player(_ipAddr, _portNo, ++currentLastPlayerId, _username, true, communicator));
            return currentLastPlayerId;
        }
    }

    public void receiveReady(String _ipAddr, int _portNo) {
        game.clientReady(_ipAddr, _portNo);
    }

    public int receiveLeaveGame(String _ipAddr, int _portNo) {
        // Returns 0: no error.
        // Returns 1: game is running.
        return game.clientLeave(_ipAddr, _portNo);
    }

    public HashSet getClientList() {
        return game.getPlayerConnected();
    }

    public void run() {
        System.out.println("New Thread Started.");
        do {
            JSONObject request = null;
            try {
                request = communicator.getLastRequestDariSeberangSana();
                System.out.println(request.toString());

                // [OK] PROTOCOL NO. 1
                if (request.get("method").equals("join")) {
                    JSONObject response = new JSONObject();
                    if (request.containsKey("udp_port") && request.containsKey("udp_address") && request.containsKey("username")){
                        this.udpIpAddress = request.get("udp_address").toString();
                        this.udpPortNumber = Integer.parseInt(request.get("udp_port").toString());
                        this.username = request.get("username").toString();
                        this.aliveStatus = true;
                        this.playerId = this.receiveJoin(request.get("udp_address").toString(), Integer.parseInt(request.get("udp_port").toString()), request.get("username").toString());
                        if (this.playerId>=0){
                            response.put("status", "ok");
                            response.put("player_id", this.playerId);
                        }else if (this.playerId==-1){
                            response.put("status", "fail");
                            response.put("description","username exists");
                        }else /*this.playerId==-2*/{
                            response.put("status", "fail");
                            response.put("description","game already started");
                        }
                    }else{
                        response.put("status","error");
                        response.put("description","required parameter not found");
                    }
                    communicator.sendResponseKeSeberangSana(response);
                    System.out.println(response.toJSONString());

                }
                // PROTOCOL NO. 2 (+ PROTOCOL NO. 12 INCLUSIVE (jangan): START GAME) TODO: Random Werewolf player and START GAME
                else if (request.get("method").equals("ready")) {
                    receiveReady(this.udpIpAddress, this.udpPortNumber);
                    JSONObject response = new JSONObject();
                    response.put("status", "ok");
                    response.put("description", "waiting for other player to start");
                    communicator.sendResponseKeSeberangSana(response);

                    receiveReady(this.udpIpAddress, this.udpPortNumber);


                }
                // PROTOCOL NO. 3
                else if (request.get("method").equals("leave")) {
                    int leaveResult = receiveLeaveGame(this.udpIpAddress, this.udpPortNumber);
                    if (leaveResult == 0) {
                        JSONObject response = new JSONObject();
                        response.put("status", "ok");
                        communicator.sendResponseKeSeberangSana(response);
                    }
                    else if (leaveResult == 1) {
                        JSONObject response = new JSONObject();
                        response.put("status", "fail");
                        response.put("description", "The game has started. You are not allowed to leave.");
                        communicator.sendResponseKeSeberangSana(response);
                    }
                }
                // PROTOCOL NO. 4 TODO: Insert Werewolf information ONLY FOR DEAD PLAYERS
                else if (request.get("method").equals("client_address")) {
                    JSONObject response = new JSONObject();
                    response.put("status", "ok");

                    Player currentPlayerConnectedIterator;

                    JSONArray playerConnectedJSONArray = new JSONArray();
                    HashSet playerConnected = game.getPlayerConnected();
                    Iterator<Player> iterator = playerConnected.iterator();
                    while (iterator.hasNext()) {
                        JSONObject playerEachConnectedJSONObject = new JSONObject();
                        currentPlayerConnectedIterator = iterator.next();
                        playerEachConnectedJSONObject.put("player_id", currentPlayerConnectedIterator.getPlayerId());
                        playerEachConnectedJSONObject.put("is_alive", currentPlayerConnectedIterator.getAliveStatus()?1:0);
                        playerEachConnectedJSONObject.put("address", currentPlayerConnectedIterator.getIpAddress());
                        playerEachConnectedJSONObject.put("port", currentPlayerConnectedIterator.getPortNumber());
                        playerEachConnectedJSONObject.put("username", currentPlayerConnectedIterator.getUsername());
                        playerConnectedJSONArray.add(playerEachConnectedJSONObject);
                    }

                    response.put("clients", playerConnectedJSONArray);
                    response.put("description", "list of clients retrieved");

                    System.out.println(response.toString());
                    communicator.sendResponseKeSeberangSana(response);
                }
                // [OK] PROTOCOL NO. 7: CLIENT ACCEPTED PROPOSAL (FOR KPU_ID)
                else if (request.get("method").equals("accepted_proposal")) {
                    JSONObject response = new JSONObject();

                    if (request.containsKey("kpu_id")){
                        int from_UID = this.playerId;
                        int acceptedValue = Integer.parseInt(request.get("kpu_id").toString());
                        game.accepted(from_UID,acceptedValue);
                        response.put("status","ok");
                    }else{
                        response.put("status","fail");
                        response.put("description","required parameter kpu_id not found");
                    }

                    communicator.sendResponseKeSeberangSana(response);
                }
                // PROTOCOL NO. 9: INFO WEREWOLF KILLED
                else if (request.get("method").equals("vote_result_werewolf")) {
                    if (Integer.parseInt(request.get("vote_status").toString())!=-1)
                        game.killPlayer(Integer.parseInt(request.get("player_killed").toString()));
                    else
                        game.noKillPlayer();
                    JSONObject response = new JSONObject();
                    response.put("status", "ok");
                    response.put("description", "player killed");
                    communicator.sendResponseKeSeberangSana(response);
                }
                // PROTOCOL NO. 11: INFO CIVILIAN KILLED
                else if (request.get("method").equals("vote_result_civilian")) {
                    if (Integer.parseInt(request.get("vote_status").toString())!=-1)
                        game.killPlayer(Integer.parseInt(request.get("player_killed").toString()));
                    else
                        game.noKillPlayer();
                    JSONObject response = new JSONObject();
                    response.put("status", "ok");
                    response.put("description", "player killed");
                    communicator.sendResponseKeSeberangSana(response);
                }
                else if (request.get("method").equals("vote_result")) { // Cannot decide
                    if (game.getDayStatus() == false) { // Werewolf must vote again and again
                        JSONObject jsonObject = new JSONObject();
                        jsonObject.put("method", "vote_now");
                        jsonObject.put("phase", "night");
//                    sendBroadcast()

                    }
                    else { // In the day time, civilian is only restricted to vote only max 2 times. Otherwise, change day
                        if (voteDayNotDecidedCount < 2) {
                            voteDayNotDecidedCount++;
                            // Resend to vote

                        }
                        else {
                            // Forcing change day

                        }
                    }
                }
                // EXCEPTIONAL UNKNOWN PROTOCOL
                else {
                    // Command not found. Wrong JSON request.
                    JSONObject jsonObjectSend = new JSONObject();
                    jsonObjectSend.put("status", "error");
                    jsonObjectSend.put("description", "wrong request");
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
        } while (!leaveStatus);
    }

    public static void main(String[] args) throws InterruptedException {

        System.out.println("port: ");
        Scanner sc = new Scanner(System.in);

        while (!sc.hasNextInt()){
            sleep(20);
        }

        int port = sc.nextInt();

        try {
            serverSocket = new ServerSocket(port);
            System.out.println(InetAddress.getLocalHost());
        } catch (IOException e) {
            e.printStackTrace();
        }

        game.start();
        serverList = new LinkedList<Server>();

        while (true) {
            System.out.println("LISTENING NEW CONNECTION...");
            Socket socket = null;
            try {
                socket = serverSocket.accept();
                System.out.println("ACCEPTED NEW CONNECTION from " + socket);
                Server newServer = new Server(socket);
                serverList.add(newServer);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}

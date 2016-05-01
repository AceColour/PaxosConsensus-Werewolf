package Client;


import Client.Communications.TCPRequestResponseChannel;
import Client.Misc.ClientInfo;
import Client.Paxos.Messenger;
import Client.Paxos.PaxosController;
import jdk.nashorn.internal.parser.JSONParser;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.ParseException;

import java.io.IOException;
import java.net.*;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import static java.net.InetAddress.*;

/**
 * Created by nim_13512501 on 4/23/16.
 */
public class Client {
    private UI ui;
    private ClientInfo playerInfo;
    private Boolean isReady;
    private Boolean isStart;
    private Boolean isKPU;
    private Boolean isDay;

    //
    private ArrayList<ClientInfo> listPlayer = new ArrayList<ClientInfo>();

    //
    private ArrayList<String> friends = new ArrayList<String>();

    //
    private int daysCount;

    private int numPlayer;

    DatagramSocket datagramSocket;

    // Paxos Controller
    PaxosController paxosController;

    // TCP
    private InetSocketAddress serverAddress;

    // UDP
    private InetSocketAddress UDPAddress;

    private TCPRequestResponseChannel communicator;


    public Client(){
        // Initialization
        ui = new CommandLineUI();
        playerInfo = new ClientInfo(-1,-1,null,-1,null);
        isKPU = false;
        isDay = false;
        numPlayer = 0;
        daysCount = 0;
    }

    public void join() throws IOException {

        // Set username
        playerInfo.setUsername(ui.askUsername());

        // Variable to determine request whether ok or not
        Boolean retryRequest = false;

        // Create JSON Object for join request
        JSONObject joinRequest = new JSONObject();
        joinRequest.put("method", "join");
        joinRequest.put("username", playerInfo.getUsername());
        joinRequest.put("udp_address", UDPAddress.getAddress().toString());
        joinRequest.put("udp_port", UDPAddress.getPort());

        do {
            // Get JSON Object as join response from server
            JSONObject joinResponse = null;
            try {
                joinResponse = new JSONObject(communicator.sendRequestAndGetResponse(joinRequest));
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            // Get status from response
            String status = joinResponse.get("status").toString();

            // Check status response from server
            if (status == null) {
                ui.displayFailedResponse("Join", "connection failure: error response from server");
                retryRequest = true;
            } else if(status.equals("ok") && joinResponse.containsKey("player_id")){
                ui.displaySuccessfulResponse("Join");
                playerInfo.setPlayerId(Integer.parseInt(joinResponse.get("player_id").toString()));
                retryRequest = false;
            } else if(status.equals("fail")) {
                ui.displayFailedResponse("Join", "connection failure: error response from server");
                retryRequest = true;
            } else if(status.equals("error")){
                ui.displayErrorResponse("Join", "error: " + joinResponse.get("description"));
                retryRequest = true;
            } else {
                ui.displayErrorResponse("Join", "error: error is undetermined");
                retryRequest = true;
            }
        }while (retryRequest); // while there is error or failed response, try send request again
    }

    public void start() {
        // listening to the port
        JSONObject recv = null;
        try {
            recv = communicator.getLastRequestDariSeberangSana();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        if(recv.get("method").equals("start")) {
            isDay = recv.get("time").equals("day");
            playerInfo.setRole(recv.get("role").toString());
            if(playerInfo.getRole().equals("werewolf")){
                friends = (ArrayList)recv.get("friends");
                //TODO debug this
            }

            // Send back response to server
            JSONObject response = new JSONObject();
            response.put("status", "ok");
            try {
                communicator.sendResponseKeSeberangSana(response);
            } catch (IOException e) {
                e.printStackTrace();
            }

        }
        daysCount++;
    }

    public void run() throws IOException, InterruptedException {
        // Get UDP Port
        int port =  ui.askPortUDP();

        try {
            UDPAddress = new InetSocketAddress(
                    getLocalHost().getHostAddress(),
                   port
            );
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }

        // Initialization socket value
        datagramSocket = new DatagramSocket(UDPAddress.getPort());

        // Debug
        System.out.println(getLocalHost().getHostAddress() + ":" + port) ;

        // Get Server address
        serverAddress = ui.askServerAddress();

        // Initializing TCP Channel for request and response
        try {
            communicator = new TCPRequestResponseChannel(
                    serverAddress.getAddress(),
                    serverAddress.getPort()
            );
            communicator.start();
        } catch (IOException e) {
            ui.displayErrorConnecting(serverAddress);
            java.lang.System.exit(1);
        }

        // Join to server
        join();

        //ready or leave
        if (ui.askReadyOrLeave()==1){

            // Send ready up request
            readyUp();

        }else{

            // Send leave request
            leave();
        }

        waitForStart();

        if(isReady && isStart) {
            play();
        }
    }

    public void waitForStart() {

        Thread leaveThread = new Thread(){
            @Override
            public void run(){

                if (ui.askLeaveWhileWaiting())
                    leave();
            }
        };

        isStart = false;
        do{
            try {
                JSONObject jsonObject = communicator.getLastRequestDariSeberangSana();

                JSONObject response = new JSONObject();

                if (jsonObject.get("method").equals("start")){
                    response.put("status", "ok");

                    Object time = null;
                    Object role = null;
                    Object friend = null;

                    if (jsonObject.containsKey("time")) time = jsonObject.get("time");
                    if (jsonObject.containsKey("role")) {
                        role = jsonObject.get("role");
                        playerInfo.setRole(role.toString());
                    }else{
                        playerInfo.setRole("civilian");
                    }
                    if (jsonObject.containsKey("friend")) friend= jsonObject.get("friend");

                    ui.displayGameStart(time, role, friend);

                    isStart = true;
                }else{
                    response.put("status", "fail");
                    response.put("description", "client cannot conform");
                }

                communicator.sendResponseKeSeberangSana(response);
            } catch (InterruptedException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }while (!isStart);

        leaveThread.interrupt();
    }

    public void leave() {
        // Variable to determine request whether ok or not
        Boolean retryRequest = false;

        // Create JSON Object for leave request
        JSONObject leaveRequest = new JSONObject();
        leaveRequest.put("method", "leave");

        do {
            // Get JSON Object as join response from server
            JSONObject leaveResponse = null;
            try {
                leaveResponse = new JSONObject(communicator.sendRequestAndGetResponse(leaveRequest));
            } catch (IOException e) {
                e.printStackTrace();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            // Get status from response
            String status = leaveResponse.get("status").toString();

            // Check status response from server
            if (status == null) {
                ui.displayFailedResponse("Leave", "connection failure: error response from server");
                retryRequest = true;
            } else if(status.equals("ok")){
                ui.displaySuccessfulResponse("Leave");
                playerInfo.setIsAlive(0);
                System.exit(0);
            } else if(status.equals("fail")) {
                ui.displayFailedResponse("Leave", "connection failure: error response from server");
                retryRequest = true;
            } else if(status.equals("error")){
                ui.displayErrorResponse("Leave", "error: " + leaveResponse.get("description"));
                retryRequest = true;
            } else {
                ui.displayErrorResponse("Leave", "error: error is undetermined");
                retryRequest = true;
            }
        }while (retryRequest); // while there is error or failed response, try send request again
    }

    public void readyUp() {
        // Variable to determine request whether ok or not
        Boolean retryRequest = false;

        // Create JSON Object for readyUp request
        JSONObject readyUpRequest = new JSONObject();
        readyUpRequest.put("method", "ready");

        do {
            // Get JSON Object as join response from server
            JSONObject readyUpResponse = null;
            try {
                readyUpResponse = new JSONObject(communicator.sendRequestAndGetResponse(readyUpRequest));
            } catch (IOException e) {
                e.printStackTrace();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            // Get status from response
            String status = readyUpResponse.get("status").toString();

            // Check status response from server
            if (status == null) {
                ui.displayFailedResponse("Retrieve List Client", "connection failure: error response from server");
                retryRequest = true;
            } else if(status.equals("ok")){
                ui.displaySuccessfulResponse("Retrieve List Client");
                isReady = true;
                retryRequest = false;
            } else if(status.equals("fail")) {
                ui.displayFailedResponse("Retrieve List Client", "connection failure: error response from server");
                retryRequest = true;
            } else if(status.equals("error")){
                ui.displayErrorResponse("Retrieve List Client", "error: " + readyUpResponse.get("description"));
                retryRequest = true;
            } else {
                ui.displayErrorResponse("Retrieve List Client", "error: error is undetermined");
                retryRequest = true;
            }
        }while (retryRequest); // while there is error or failed response, try send request again
    }

    public void changePhase(JSONObject recv) {
        // listening to the port
        if(recv.get("method").equals("change_phase")) {
            isDay = recv.get("time").equals("day");
            daysCount = Integer.parseInt(recv.get("days").toString());

            // Send back response to server
            JSONObject response = new JSONObject();
            response.put("status", "ok");
            try {
                communicator.sendResponseKeSeberangSana(response);

                // Retrieve the latest client list
                retrieveListClient();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void retrieveListClient() throws UnknownHostException {

        // Variable to determine request whether ok or not
        Boolean retryRequest = false;

        // Create JSON Object for listClient request
        JSONObject listClientRequest = new JSONObject();
        listClientRequest.put("method", "client_address");

        do {
            // Get JSON Object as join response from server
            JSONObject listClientResponse = null;
            try {
                listClientResponse = new JSONObject(communicator.sendRequestAndGetResponse(listClientRequest));
            } catch (IOException e) {
                e.printStackTrace();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            // Get status from response
            String status = listClientResponse.get("status").toString();

            // Check status response from server
            if (status == null) {
                ui.displayFailedResponse("Retrieve List Client", "connection failure: error response from server");
                retryRequest = true;
            } else if(status.equals("ok")){
                ui.displaySuccessfulResponse("Retrieve List Client");

                // Clear list player
                listPlayer.clear();

                // Iterating client's array
                JSONArray slideContent = (JSONArray) listClientResponse.get("clients");
                Iterator i = slideContent.iterator();

                //TODO debug this
                while (i.hasNext()) {
                    JSONObject clientJSON = (JSONObject) i.next();

                    // Add client to list Player
                    listPlayer.add(new ClientInfo(
                            Integer.parseInt (clientJSON.get("player_id").toString()),
                            Integer.parseInt (clientJSON.get("is_alive").toString()),
                            getByName((String)clientJSON.get("address")),
                            Integer.parseInt (clientJSON.get("port").toString()),
                            (String)clientJSON.get("username")
                    ));
                }

                ui.displayListClient(listPlayer);

            } else if(status.equals("fail")) {
                ui.displayFailedResponse("Retrieve List Client", "connection failure: error response from server");
                retryRequest = true;
            } else if(status.equals("error")){
                ui.displayErrorResponse("Retrieve List Client", "error: " + listClientResponse.get("description"));
                retryRequest = true;
            } else {
                ui.displayErrorResponse("Retrieve List Client", "error: error is undetermined");
                retryRequest = true;
            }
        }while (retryRequest); // while there is error or failed response, try send request again
    }

    boolean gameOver;

    int kpu_id;


    //TODO refactor ini
    public void play() throws IOException, InterruptedException {
        gameOver = false;


        do{

           JSONObject serverCommand = communicator.getLastRequestDariSeberangSana();
            //change phase
           if (serverCommand.get("method").equals("change_phase")){
               changePhase(serverCommand);

                if (isDay) {
                    //TODO show civilian killed if day and not gameover
                    //TODO show werewolf/civilian killed if night and not gameover

                    doPaxos();
                }
           }else if (serverCommand.get("method").equals("vote_now")){
               JSONObject response = new JSONObject();
               response.put("status","ok");

               System.out.println("VOTING NOT IMPLEMENTED");
            //TODO run voting process
           }else if (serverCommand.get("method").equals("game_over")){
                gameOver(serverCommand);
           }

        }while (!gameOver);

    }

    public int countActivePlayers() {
        // Return the number of active players
        int count = 0;
        for (int i=0; i<listPlayer.size(); i++)
            if (listPlayer.get(i).getIsAlive() == 1)
                count++;
        return count;
    }

    /* DOING PAXOS */
    public void doPaxos() throws SocketException {
        //run paxos if day and not gameover
        paxosController = new PaxosController(listPlayer, playerInfo.getPlayerId(), datagramSocket);
        paxosController.start();

        //tunggu perintah vote dari server
        //periksa lagi apakah perintah vote itu sebelum atau sesudah paxos
        boolean KPUSelected = false;
        try {
            do {
                JSONObject message = null;
                    message = communicator.getLastRequestDariSeberangSana();
                if (message.get("method").equals("game_over")) {
                    gameOver = true;
                    JSONObject response = new JSONObject();
                    response.put("status", "ok");
                    communicator.sendResponseKeSeberangSana(response);
                } else if (message.get("method").equals("kpu_selected")) {
                    JSONObject response = new JSONObject();
                    if (message.containsKey("kpu_id")){
                        response.put("status", "ok");
                        kpu_id = (Integer) message.get("kpu_id");
                    }else{
                        response.put("status", "error");
                        response.put("description", "value not found");
                    }
                    communicator.sendResponseKeSeberangSana(response);
                } else {
                    JSONObject response = new JSONObject();
                    response.put("status", "fail");
                    response.put("description", "client cannot conform");
                    communicator.sendResponseKeSeberangSana(response);
                }
            } while (!KPUSelected);
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

        paxosController.stopPaxos();
    }

    public void gameOver(JSONObject serverCommand){
        if (serverCommand.containsKey("winner")){
            JSONObject response = new JSONObject();
            response.put("status", "ok");
            try {
                communicator.sendResponseKeSeberangSana(response);
            } catch (IOException e) {
                e.printStackTrace();
            }

            ui.displayGameOver((String) serverCommand.get("winner"));
            gameOver = true;

        }else{
            JSONObject response = new JSONObject();
            response.put("status", "fail");
            response.put("description","no winner?");
            try {
                communicator.sendResponseKeSeberangSana(response);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
    /* VOTING */

    public void voteKillWerewolf(int playerId) {
        if(playerInfo.getRole().equals("werewolf") && !isDay){
            // Create JSON Object for killWerewolf request
            JSONObject killWerewolfRequest = new JSONObject();
            killWerewolfRequest.put("method", "vote_werewolf");
            killWerewolfRequest.put("player_id", ui.killWerewolfId());

            Messenger.sendJSONObject(killWerewolfRequest, datagramSocket, getSocketAddressFromPlayerId(playerId));
        }
    }

    public void voteKillCivilian(int playerId) {
        if(playerInfo.getRole().equals("civilian") && isDay){

            // Create JSON Object for killWerewolf request
            JSONObject killCivilianRequest = new JSONObject();
            killCivilianRequest.put("method", "vote_civilian");
            killCivilianRequest.put("player_id", ui.killCivilianId());

            Messenger.sendJSONObject(killCivilianRequest, datagramSocket, getSocketAddressFromPlayerId(playerId));
        }
    }

    public InetSocketAddress getSocketAddressFromPlayerId(int playerId) {
        for (ClientInfo clientInfo: listPlayer){
            if (clientInfo.getPlayerId() == playerId){
                InetSocketAddress inetSocketAddress = new InetSocketAddress(
                        clientInfo.getAddress(),
                        clientInfo.getPort());
            }
        }
        return null;
    }

    public static void main(String [] args) throws IOException, InterruptedException {
        Client client = new Client();

        try {
            client.run();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

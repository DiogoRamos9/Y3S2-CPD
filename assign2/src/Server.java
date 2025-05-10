import java.net.*;
import java.io.*;
import java.util.concurrent.ConcurrentHashMap;

public class Server {
    private static final int PORT = 8080;
    private static final String HOST = "0.0.0.0"; // Accept connections from any IP address
    private static ServerSocket serverSocket;
    private static ConcurrentHashMap<Socket, String> clientUsernames = new ConcurrentHashMap<>();
    private static ConcurrentHashMap<String, ConcurrentHashMap<Socket, String>> chatRooms = new ConcurrentHashMap<>();

    public static void main(String[] args) {
        try {
            // Initialize the default "general" chat room
            chatRooms.put("general", new ConcurrentHashMap<>());

            serverSocket = new ServerSocket(PORT);
            System.out.println("Server started on " + HOST + ":" + PORT);
            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("Client connected: " + clientSocket.getInetAddress());
                
                // Handle client connection in a separate thread to allow multiple clients
                // to connect simultaneously and to avoid blocking the main thread.
                new Thread(() -> handleClient(clientSocket)).start();

                if (clientSocket.isClosed() || !clientSocket.isConnected()) {
                    System.out.println("Client disconnected: " + clientSocket.getInetAddress());
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                if (serverSocket != null && !serverSocket.isClosed()) {
                    serverSocket.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private static void handleClient(Socket clientSocket) {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
             PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true)) {

            // Lê o username do client e o armazena no mapa
            String username = in.readLine();
            clientUsernames.put(clientSocket, username);
            chatRooms.get("general").put(clientSocket, username);
            System.out.println(username + " has joined the server and the 'general' room.");

            out.println("Welcome to the server, " + username + "!");
            out.println("You are in the 'general' room by default.");
            out.println("Use /create <room_name>, /join <room_name>, or /leave to manage chat rooms.");

            String currentRoom = "general"; // Default room
            String inputLine;
            while ((inputLine = in.readLine()) != null) {
                if (inputLine.startsWith("/create ")) {
                    String roomName = inputLine.substring(8).trim();
                    if (chatRooms.containsKey(roomName)) {
                        out.println("Chat room already exists.");
                    } else {
                        chatRooms.put(roomName, new ConcurrentHashMap<>());
                        out.println("Chat room '" + roomName + "' created.");
                    }
                } else if (inputLine.startsWith("/join ")) {
                    String roomName = inputLine.substring(6).trim();
                    if (!chatRooms.containsKey(roomName)) {
                        out.println("Chat room does not exist.");
                    } else {
                        chatRooms.get(currentRoom).remove(clientSocket);
                        currentRoom = roomName;
                        chatRooms.get(roomName).put(clientSocket, username);
                        out.println("Joined chat room '" + roomName + "'.");
                    }
                } else if (inputLine.equals("/leave")) {
                    if (!currentRoom.equals("general")) {
                        chatRooms.get(currentRoom).remove(clientSocket);
                        currentRoom = "general";
                        chatRooms.get("general").put(clientSocket, username);
                        out.println("You have returned to the 'general' room.");
                    } else {
                        out.println("You are already in the 'general' room.");
                    }
                } else {
                    // Log the message in the server terminal
                    System.out.println(currentRoom + "/" + username + ": " + inputLine);

                    // Broadcast message to the current room
                    for (Socket socket : chatRooms.get(currentRoom).keySet()) {
                        if (!socket.equals(clientSocket)) {
                            new PrintWriter(socket.getOutputStream(), true).println(username + ": " + inputLine);
                        }
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                String username = clientUsernames.remove(clientSocket);
                if (username != null) {
                    System.out.println(username + " has disconnected.");
                    chatRooms.get("general").remove(clientSocket);
                }
                clientSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
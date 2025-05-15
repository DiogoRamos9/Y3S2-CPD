import java.net.*;
import java.io.*;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

public class Server {
    private static final int PORT = 8080;
    private static final String HOST = "0.0.0.0";
    private static ServerSocket serverSocket;

    // Shared data structures
    private static final Map<Socket, String> clientUsernames = new HashMap<>();
    private static final Map<String, Map<Socket, String>> chatRooms = new HashMap<>();

    private static final ReentrantLock clientUsernamesLock = new ReentrantLock();
    private static final ReentrantLock chatRoomsLock = new ReentrantLock();

    public static void main(String[] args) {
        try {
 
            chatRoomsLock.lock();
            try {
                chatRooms.put("general", new HashMap<>());
            } finally {
                chatRoomsLock.unlock();
            }

            serverSocket = new ServerSocket(PORT);
            System.out.println("Server started on " + HOST + ":" + PORT);
            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("Client connected: " + clientSocket.getInetAddress());
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

            String username = in.readLine();

            clientUsernamesLock.lock();
            try {
                clientUsernames.put(clientSocket, username);
            } finally {
                clientUsernamesLock.unlock();
            }

            chatRoomsLock.lock();
            try {
                chatRooms.get("general").put(clientSocket, username);
            } finally {
                chatRoomsLock.unlock();
            }

            System.out.println(username + " has joined the server and the 'general' room.\n");

            out.println("Welcome to the server, " + username + "!");
            out.println("You are in the 'general' room by default.");
            out.println("Use /create <room_name>, /join <room_name>, /leave to manage chat rooms and /list .");

            String currentRoom = "general";
            String inputLine;
            while ((inputLine = in.readLine()) != null) {
                if (inputLine.startsWith("/create ")) {
                    String roomName = inputLine.substring(8).trim();
                    chatRoomsLock.lock();
                    try {
                        if (chatRooms.containsKey(roomName)) {
                            out.println("Chat room already exists.");
                        } else {
                            chatRooms.put(roomName, new HashMap<>());
                            out.println("Chat room '" + roomName + "' created.");
                        }
                    } finally {
                        chatRoomsLock.unlock();
                    }
                } else if (inputLine.startsWith("/join ")) {
                    String roomName = inputLine.substring(6).trim();
                    chatRoomsLock.lock();
                    try {
                        if (!chatRooms.containsKey(roomName)) {
                            chatRooms.put(roomName, new HashMap<>());
                            out.println("Chat room '" + roomName + "' created.");
                            
                        } 
                        chatRooms.get(currentRoom).remove(clientSocket);
                        currentRoom = roomName;
                        chatRooms.get(roomName).put(clientSocket, username);
                        out.println("Joined chat room '" + roomName + "'.");

                    } finally {
                        chatRoomsLock.unlock();
                    }
                } else if (inputLine.equals("/leave")) {
                    chatRoomsLock.lock();
                    try {
                        if (!currentRoom.equals("general")) {
                            chatRooms.get(currentRoom).remove(clientSocket);
                            currentRoom = "general";
                            chatRooms.get("general").put(clientSocket, username);
                            out.println("You have returned to the 'general' room.");
                        } else {
                            out.println("You are already in the 'general' room.");
                        }
                    } finally {
                        chatRoomsLock.unlock();
                    }
                } else if (inputLine.equals("/list")) {
                    chatRoomsLock.lock();
                    try {
                        out.println("Available chat rooms:");
                        for (String room : chatRooms.keySet()) {
                            out.println("- " + room);
                        }
                    } finally {
                        chatRoomsLock.unlock();
                    }
                } else {
                    // Log the message in the server terminal
                    System.out.println(currentRoom + "/" + username + ": " + inputLine);

                    // Broadcast message to the current room
                    chatRoomsLock.lock();
                    try {
                        for (Socket socket : chatRooms.get(currentRoom).keySet()) {
                            if (!socket.equals(clientSocket)) {
                                new PrintWriter(socket.getOutputStream(), true)
                                    .println(username + ": " + inputLine);
                            }
                        }
                    } finally {
                        chatRoomsLock.unlock();
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                String username;
                clientUsernamesLock.lock();
                try {
                    username = clientUsernames.remove(clientSocket);
                } finally {
                    clientUsernamesLock.unlock();
                }
                if (username != null) {
                    System.out.println(username + " has disconnected.");
                    chatRoomsLock.lock();
                    try {
                        chatRooms.get("general").remove(clientSocket);
                    } finally {
                        chatRoomsLock.unlock();
                    }
                }
                clientSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
import java.net.*;
import java.io.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

public class Server {
    private static final int PORT = 8080;
    private static final String HOST = "0.0.0.0";
    private static ServerSocket serverSocket;

    // Shared data structures
    private static final Map<Socket, String> clientUsernames = new HashMap<>(); // Socket: username
    private static final Map<String, Map<Socket, String>> chatRooms = new HashMap<>();  // Room name: (Socket: username)
    private static final Map<String, String> aiRoomPrompts = new HashMap<>(); // roomName -> prompt
    private static final Map<String, List<String>> aiRoomHistory = new HashMap<>(); // roomName -> message history

    private static final ReentrantLock clientUsernamesLock = new ReentrantLock();  // Lock for clientUsernames 
    private static final ReentrantLock chatRoomsLock = new ReentrantLock();  // Lock for chatRooms

    public static void main(String[] args) {
        UserManager.setupUsers();
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
                Thread.startVirtualThread(() -> handleClient(clientSocket));

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

            // Every time the maps are accessed, the locks are acquired and released to prevent race conditions
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

            System.out.println(username + " has joined the server and the 'general' room.");

            out.println("Welcome to the server, " + username + "!");
            out.println("You are in the 'general' room by default.");
            out.println("List of commands:");
            out.println("/create <room_name> - Create a new chat room");
            out.println("/join <room_name> - Join an existing chat room");
            out.println("/leave - Leave the current chat room and return to 'general'");
            out.println("/rooms - List all available chat rooms");
            out.println("/users - List all user on the current room");
            out.println("/help - Show this help message");
            out.println("/exit - Exit the client");
            if (isAdmin(username)) {
                out.println("You are an admin, you can use the following commands:");
                out.println("/ban <username> - Ban a user from the server");
                out.println("/mute <username> - Temporarily prevent a user from sending messages");
                out.println("/unmute <username> - Allow a muted user to send messages again");
                out.println("/announce <message> - Send an announcement to all chat rooms");
                out.println("/promote <username> - Promote a user to admin role");
                out.println("/demote <username> - Demote an admin to regular user");
                out.println("/stats - Show server statistics and active connections");

            } else {
                out.println("You are a regular user.");
            }

            String currentRoom = "general";
            String inputLine;
            while ((inputLine = in.readLine()) != null) {
                // Commands for every user
                if (inputLine.startsWith("/create ")) {
                    String roomSpec = inputLine.substring(8).trim();
                    chatRoomsLock.lock();
                    try {
                        if (chatRooms.containsKey(roomSpec)) {
                            out.println("Chat room already exists.");
                        } else if (roomSpec.startsWith("ai:")) {
                            // AI room: format is ai:<room_name>:<prompt>
                            String[] parts = roomSpec.split(":", 3);
                            if (parts.length < 3) {
                                out.println("Invalid AI room format. Use ai:<room_name>:<prompt>");
                            } else {
                                String aiRoomName = parts[1];
                                String prompt = parts[2];
                                chatRooms.put(aiRoomName, new HashMap<>());
                                aiRoomPrompts.put(aiRoomName, prompt);
                                aiRoomHistory.put(aiRoomName, new java.util.ArrayList<>());
                                out.println("AI chat room '" + aiRoomName + "' created with prompt: " + prompt);
                            }
                        } else {
                            chatRooms.put(roomSpec, new HashMap<>());
                            out.println("Chat room '" + roomSpec + "' created.");
                        }
                    } finally {
                        chatRoomsLock.unlock();
                    }
                }
                else if (inputLine.startsWith("/join ")) {
                    String roomName = inputLine.substring(6).trim();
                    chatRoomsLock.lock();
                    try {
                        if (!chatRooms.containsKey(roomName)) {
                            chatRooms.put(roomName, new HashMap<>());
                            out.println("Chat room '" + roomName + "' created.");
                        } 
                        // Remove the user from the current room and join the new room
                        chatRooms.get(currentRoom).remove(clientSocket);
                        currentRoom = roomName;
                        chatRooms.get(roomName).put(clientSocket, username);
                        out.println("Joined chat room '" + roomName + "'.");

                        // Notify other users in the new room
                        for (Socket socket : chatRooms.get(roomName).keySet()) {
                            if (!socket.equals(clientSocket)) {
                                new PrintWriter(socket.getOutputStream(), true)
                                    .println(username + " has joined the room.");
                            }
                        }
                    } finally {
                        chatRoomsLock.unlock();
                    }
                }
                else if (inputLine.equals("/leave")) {
                    chatRoomsLock.lock();
                    try {
                        if (!currentRoom.equals("general")) {
                            String previousRoom = currentRoom;
                            
                            // Notify other users in the previous room that the user has left
                            for (Socket socket : chatRooms.get(previousRoom).keySet()) {
                                if (!socket.equals(clientSocket)) {
                                    new PrintWriter(socket.getOutputStream(), true)
                                        .println(username + " has left the room.");
                                }
                            }
                            
                            // Remove the user from the previous room and join the general room
                            chatRooms.get(previousRoom).remove(clientSocket);
                            currentRoom = "general";
                            chatRooms.get("general").put(clientSocket, username);
                            out.println("You have returned to the 'general' room.");

                            // Notify other users in the general room that the user has returned
                            for (Socket socket : chatRooms.get("general").keySet()) {
                                if (!socket.equals(clientSocket)) {
                                    new PrintWriter(socket.getOutputStream(), true)
                                        .println(username + " has returned to the 'general' room.");
                                }
                            }
                        } else {
                            out.println("You are already in the 'general' room.");
                        }
                    } finally {
                        chatRoomsLock.unlock();
                    } 
                }
                else if (inputLine.equals("/rooms")) {
                    chatRoomsLock.lock();
                    try {
                        // Print the list of available chat rooms
                        out.println("Available chat rooms:");
                        for (String room : chatRooms.keySet()) {
                            out.println("- " + room);
                        }
                    } finally {
                        chatRoomsLock.unlock();
                    }
                }
                else if (inputLine.equals("/users")) {
                    chatRoomsLock.lock();
                    try {
                        // Print the list of users in the current room
                        out.println("Users in the current room (" + currentRoom + "):");
                        for (String user : chatRooms.get(currentRoom).values()) {
                            out.println("- " + user);
                        }
                    } finally {
                        chatRoomsLock.unlock();
                    }
                }
                // Commands for admins
                else if (inputLine.startsWith("/ban ")) {
                    if (isAdmin(username)) {
                        // Ban a user from the server
                        String userToBan = inputLine.substring(5).trim();
                        banUser(userToBan, username, out);
                    }
                    else {
                        out.println("You do not have permission to ban users.");
                    }
                }
                else if (inputLine.startsWith("/mute ")) {
                    if (isAdmin(username)) {
                        // Mute a user
                        String userToMute = inputLine.substring(6).trim();
                        muteUser(userToMute, username, out);
                    } else {
                        out.println("You do not have permission to mute users.");
                    }
                }
                else if (inputLine.startsWith("/unmute ")) {
                    if (isAdmin(username)) {
                        // Unmute a user
                        String userToUnmute = inputLine.substring(8).trim();
                        unmuteUser(userToUnmute, username, out);
                    } else {
                        out.println("You do not have permission to unmute users.");
                    }
                } 
                else if (inputLine.startsWith("/announce ")) {
                    if (isAdmin(username)) {
                        // Send an announcement to all chat rooms
                        String announcement = inputLine.substring(10).trim();
                        broadcastAnnouncement(announcement, username);
                        out.println("Announcement sent to all rooms.");
                    } else {
                        out.println("You do not have permission to make announcements.");
                    }
                } 
                else if (inputLine.startsWith("/promote ")) {
                    if (isAdmin(username)) {
                        // Set the user role to admin
                        String userToPromote = inputLine.substring(9).trim();
                        promoteUser(userToPromote, username, out);
                    } else {
                        out.println("You do not have permission to promote users.");
                    }
                } 
                else if (inputLine.startsWith("/demote ")) {
                    if (isAdmin(username)) {
                        // Set the user role to regular user
                        String userToDemote = inputLine.substring(8).trim();
                        demoteUser(userToDemote, username, out);
                    } else {
                        out.println("You do not have permission to demote users.");
                    }
                } 
                else if (inputLine.equals("/stats")) {
                    if (isAdmin(username)) {
                        // Display server statistics
                        displayServerStats(out);
                    } else {
                        out.println("You do not have permission to view server statistics.");
                    }
                }
                
                // The message is not a command
                else {
                    boolean isMuted = UserManager.isUserMuted(username);
                    if (isMuted) {
                        out.println("You are currently muted and cannot send messages.");
                        continue;
                    }
                    System.out.println(currentRoom + "/" + username + ": " + inputLine);

                    chatRoomsLock.lock();
                    try {
                        // Broadcast to users as before
                        for (Socket socket : chatRooms.get(currentRoom).keySet()) {
                            if (!socket.equals(clientSocket)) {
                                final String msgUsername = username;
                                final String msgInputLine = inputLine;
                                Thread.startVirtualThread(() -> {
                                    try {
                                        new PrintWriter(socket.getOutputStream(), true)
                                            .println(msgUsername + ": " + msgInputLine);
                                    } catch (Exception e) {
                                        System.out.println("Error sending message to " + socket.getInetAddress());
                                        e.printStackTrace();
                                    }
                                });
                            }
                        }
                        // If AI room, update history and call LLM
                        if (aiRoomPrompts.containsKey(currentRoom)) {
                            List<String> history = aiRoomHistory.get(currentRoom);
                            history.add(username + ": " + inputLine);
                            String prompt = aiRoomPrompts.get(currentRoom);
                            String context = prompt + "\n" + String.join("\n", history);
                            String botReply = callLLM(context); // Implement this method to call Ollama or other LLM
                            history.add("Bot: " + botReply);
                            // Broadcast bot reply
                            for (Socket socket : chatRooms.get(currentRoom).keySet()) {
                                Thread.startVirtualThread(() -> {
                                    try {
                                        new PrintWriter(socket.getOutputStream(), true)
                                            .println("Bot: " + botReply);
                                    } catch (Exception e) {
                                        System.out.println("Error sending bot message to " + socket.getInetAddress());
                                        e.printStackTrace();
                                    }
                                });
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
                // Remove the client from the chat and close the socket and it is disconnected
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

    // Helper methods for admin commands
    private static boolean isAdmin(String username) {
        return UserManager.isAdmin(username);
    }
    
    // Disconnect a user from the server
    private static void banUser(String userToBan, String adminUsername, PrintWriter adminOut) {
        Socket userSocket = null;
        
        clientUsernamesLock.lock();
        try {
            for (Map.Entry<Socket, String> entry : clientUsernames.entrySet()) {
                if (entry.getValue().equals(userToBan)) {
                    userSocket = entry.getKey();
                    break;
                }
            }
        } finally {
            clientUsernamesLock.unlock();
        }
        
        if (userSocket == null) {
            adminOut.println("User " + userToBan + " not found on server.");
            return;
        }
        
        try {
            new PrintWriter(userSocket.getOutputStream(), true)
                .println("You have been banned from the server by admin " + adminUsername);
        } catch (IOException e) {
            e.printStackTrace();
        }
        
        try {
            userSocket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        
        adminOut.println("User " + userToBan + " has been banned from the server.");
    }

    // Mute a user indefinitely
    private static void muteUser(String userToMute, String adminUsername, PrintWriter adminOut) {
        if (UserManager.isUserMuted(userToMute)) {
            adminOut.println("User " + userToMute + " is already muted.");
            return;
        }
        UserManager.muteUser(userToMute);
        adminOut.println("User " + userToMute + " has been muted.");
        
        Socket userSocket = findUserSocketByUsername(userToMute);
        if (userSocket != null) {
            try {
                new PrintWriter(userSocket.getOutputStream(), true)
                    .println("You have been muted by admin " + adminUsername);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
    
    // Unmute a user that was previously muted
    private static void unmuteUser(String userToUnmute, String adminUsername, PrintWriter adminOut) {
        if (UserManager.isUserMuted(userToUnmute)) {
            UserManager.unmuteUser(userToUnmute);
            adminOut.println("User " + userToUnmute + " has been unmuted.");
            
            Socket userSocket = findUserSocketByUsername(userToUnmute);
            if (userSocket != null) {
                try {
                    new PrintWriter(userSocket.getOutputStream(), true)
                        .println("You have been unmuted by admin " + adminUsername);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        } else {
            adminOut.println("User " + userToUnmute + " is not muted.");
        }
    }
    
    // Promote a user to admin role
    private static void promoteUser(String userToPromote, String adminUsername, PrintWriter adminOut) {
        if (UserManager.isAdmin(userToPromote)) {
            adminOut.println("User " + userToPromote + " is already an admin.");
            return;
        }
        UserManager.promoteToAdmin(userToPromote);
        adminOut.println("User " + userToPromote + " has been promoted to admin by " + adminUsername + ".");
        
        Socket userSocket = findUserSocketByUsername(userToPromote);
        if (userSocket != null) {
            try {
                new PrintWriter(userSocket.getOutputStream(), true)
                    .println("You have been promoted to admin by " + adminUsername);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
    
    // Demote an admin to regular user
    private static void demoteUser(String userToDemote, String adminUsername, PrintWriter adminOut) {
        if (UserManager.isAdmin(userToDemote)) {
            UserManager.demoteToUser(userToDemote);
            adminOut.println("User " + userToDemote + " has been demoted to regular user.");
            
            Socket userSocket = findUserSocketByUsername(userToDemote);
            if (userSocket != null) {
                try {
                    new PrintWriter(userSocket.getOutputStream(), true)
                        .println("You have been demoted to regular user by " + adminUsername);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        } else {
            adminOut.println("User " + userToDemote + " is not an admin.");
        }
    }

    // Display basic server statistics
    private static void displayServerStats(PrintWriter adminOut) {
        chatRoomsLock.lock();
        clientUsernamesLock.lock();
        try {
            adminOut.println("=== SERVER STATISTICS ===");
            adminOut.println("Total connected users: " + clientUsernames.size());
            adminOut.println("Total chat rooms: " + chatRooms.size());
            
            adminOut.println("\nUsers per room:");
            for (Map.Entry<String, Map<Socket, String>> entry : chatRooms.entrySet()) {
                adminOut.println("- " + entry.getKey() + ": " + entry.getValue().size() + " users");
            }
            
            List<String> mutedList = UserManager.getMutedUsersList();
            adminOut.println("\nMuted users: " + mutedList.size());
            if (!mutedList.isEmpty()) {
                for (String user : mutedList) {
                    adminOut.println("- " + user);
                }
            }
            
        } finally {
            chatRoomsLock.unlock();
            clientUsernamesLock.unlock();
        }
    }
    
    // Broadcast an announcement to all chat rooms
    private static void broadcastAnnouncement(String announcement, String adminUsername) {
        chatRoomsLock.lock();
        try {
            String formattedMessage = "[ANNOUNCEMENT FROM " + adminUsername + "]: " + announcement;
            
            // Iterate through all chat rooms
            for (Map.Entry<String, Map<Socket, String>> roomEntry : chatRooms.entrySet()) {
                // Iterate through all sockets in the room and send the announcement
                for (Socket socket : roomEntry.getValue().keySet()) {
                    try {
                        new PrintWriter(socket.getOutputStream(), true)
                            .println(formattedMessage);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        } finally {
            chatRoomsLock.unlock();
        }
    }
    
    // Helper method to find a user's socket by username
    private static Socket findUserSocketByUsername(String username) {
        clientUsernamesLock.lock();
        try {
            // Iterate through all sockets and usernames to find the matching one
            for (Map.Entry<Socket, String> entry : clientUsernames.entrySet()) {
                if (entry.getValue().equals(username)) {
                    return entry.getKey();
                }
            }
            return null;
        } finally {
            clientUsernamesLock.unlock();
        }
    }

    private static String callLLM(String context) {
        // TODO: Implement HTTP call to local LLM (e.g., Ollama)
        // For now, return a dummy response
        return "This is a placeholder AI response.";
    }
}

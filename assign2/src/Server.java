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
    private static final Map<Socket, String> clientUsernames = new HashMap<>();
    private static final Map<String, Map<Socket, String>> chatRooms = new HashMap<>();
    private static final Map<User, Socket> userSockets = new HashMap<>();

    private static final ReentrantLock usersLock = new ReentrantLock();
    private static final ReentrantLock clientUsernamesLock = new ReentrantLock();
    private static final ReentrantLock chatRoomsLock = new ReentrantLock();

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
                        for (Socket socket : chatRooms.get(roomName).keySet()) {
                            if (!socket.equals(clientSocket)) {
                                new PrintWriter(socket.getOutputStream(), true)
                                    .println(username + " has joined the room.");
                            }
                        }
                    } finally {
                        chatRoomsLock.unlock();
                    }
                } else if (inputLine.equals("/leave")) {
                    chatRoomsLock.lock();
                    try {
                        if (!currentRoom.equals("general")) {
                            String previousRoom = currentRoom;
                            
                            for (Socket socket : chatRooms.get(previousRoom).keySet()) {
                                if (!socket.equals(clientSocket)) {
                                    new PrintWriter(socket.getOutputStream(), true)
                                        .println(username + " has left the room.");
                                }
                            }
                            
                            chatRooms.get(previousRoom).remove(clientSocket);
                            currentRoom = "general";
                            chatRooms.get("general").put(clientSocket, username);
                            out.println("You have returned to the 'general' room.");

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
                } else if (inputLine.equals("/users")) {
                    chatRoomsLock.lock();
                    try {
                        out.println("Users in the current room (" + currentRoom + "):");
                        for (String user : chatRooms.get(currentRoom).values()) {
                            out.println("- " + user);
                        }
                    } finally {
                        chatRoomsLock.unlock();
                    }
                } else if (inputLine.equals("/help")) {
                    out.println("List of commands:");
                    out.println("/create <room_name> - Create a new chat room");
                    out.println("/join <room_name> - Join an existing chat room");
                    out.println("/leave - Leave the current chat room and return to 'general'");
                    out.println("/rooms - List all available chat rooms");
                    out.println("/users - List all users in the current room");
                    out.println("/help - Show this help message");
                    out.println("/exit - Exit the client");
                } else if (inputLine.equals("/rooms")) {
                    chatRoomsLock.lock();
                    try {
                        out.println("Available chat rooms:");
                        for (String room : chatRooms.keySet()) {
                            out.println("- " + room);
                        }
                    } finally {
                        chatRoomsLock.unlock();
                    }
                } else if (inputLine.equals("/users")) {
                    chatRoomsLock.lock();
                    try {
                        out.println("Users in the current room (" + currentRoom + "):");
                        for (String user : chatRooms.get(currentRoom).values()) {
                            out.println("- " + user);
                        }
                    } finally {
                        chatRoomsLock.unlock();
                    }
                } else if (inputLine.startsWith("/kick ") && isAdmin(username)) {
                    String userToKick = inputLine.substring(6).trim();
                    kickUser(userToKick, currentRoom, username, out);
                } else if (inputLine.startsWith("/ban ") && isAdmin(username)) {
                    String userToBan = inputLine.substring(5).trim();
                    banUser(userToBan, username, out);
                } else if (inputLine.startsWith("/delete ") && isAdmin(username)) {
                    String roomToDelete = inputLine.substring(8).trim();
                    deleteRoom(roomToDelete, username, out, currentRoom);
                } else if (inputLine.startsWith("/mute ") && isAdmin(username)) {
                    String userToMute = inputLine.substring(6).trim();
                    muteUser(userToMute, username, out);
                } else if (inputLine.startsWith("/unmute ") && isAdmin(username)) {
                    String userToUnmute = inputLine.substring(8).trim();
                    unmuteUser(userToUnmute, username, out);
                } else if (inputLine.startsWith("/announce ") && isAdmin(username)) {
                    String announcement = inputLine.substring(10).trim();
                    broadcastAnnouncement(announcement, username);
                    out.println("Announcement sent to all rooms.");
                } else if (inputLine.startsWith("/promote ") && isAdmin(username)) {
                    String userToPromote = inputLine.substring(9).trim();
                    promoteUser(userToPromote, username, out);
                } else if (inputLine.startsWith("/demote ") && isAdmin(username)) {
                    String userToDemote = inputLine.substring(8).trim();
                    demoteUser(userToDemote, username, out);
                } else if (inputLine.equals("/stats") && isAdmin(username)) {
                    displayServerStats(out);
                } else if (inputLine.startsWith("/announce ") && isAdmin(username)) {
                    String announcement = inputLine.substring(10).trim();
                    broadcastAnnouncement(announcement, username);
                    out.println("Announcement sent to all rooms.");
                } else {
                    boolean isMuted = UserManager.isUserMuted(username);
                    
                    if (isMuted) {
                        out.println("You are currently muted and cannot send messages.");
                        continue;
                    }
                    // Log the message in the server terminal
                    System.out.println(currentRoom + "/" + username + ": " + inputLine);

                    // Broadcast message to the current room
                    chatRoomsLock.lock();
                    try {
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

// Adicionar no final da classe Server (antes do fechamento da chave)
    private static boolean isAdmin(String username) {
        return UserManager.isAdmin(username);
    }

    private static void kickUser(String userToKick, String room, String adminUsername, PrintWriter adminOut) {
        chatRoomsLock.lock();
        try {
            if (!chatRooms.containsKey(room)) {
                adminOut.println("Room does not exist.");
                return;
            }

            if (!chatRooms.get(room).containsValue(userToKick)) {
                adminOut.println("User " + userToKick + " not found in this room.");
                return;
            }

            if (room.equals("general")) {
                adminOut.println("You cannot kick users from the 'general' room.");
                return;
            }
            
            Socket userSocket = null;
            for (Map.Entry<Socket, String> entry : chatRooms.get(room).entrySet()) {
                if (entry.getValue().equals(userToKick)) {
                    userSocket = entry.getKey();
                    break;
                }
            }
            
            if (userSocket == null) {
                adminOut.println("User " + userToKick + " not found in this room.");
                return;
            }
            
            // Notifica o usuário que ele foi kickado
            try (PrintWriter userWriter = new PrintWriter(userSocket.getOutputStream(), true)) {
                userWriter.println("You have been kicked from the room by admin " + adminUsername);
                userWriter.println("You have been moved to the 'general' room.");
            } catch (IOException e) {
                e.printStackTrace();
            }
            
            // Remove do room atual e coloca no general
            chatRooms.get(room).remove(userSocket);
            chatRooms.get("general").put(userSocket, userToKick);
            
            // Notifica admin que a operação foi bem sucedida
            adminOut.println("User " + userToKick + " has been kicked from the room and moved to 'general'.");
            
            // Notifica outros na sala
            for (Socket socket : chatRooms.get(room).keySet()) {
                try (PrintWriter socketWriter = new PrintWriter(socket.getOutputStream(), true)) {
                    socketWriter.println("User " + userToKick + " has been kicked from the room by admin " + adminUsername);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        } finally {
            chatRoomsLock.unlock();
        }
    }
    
    private static void banUser(String userToBan, String adminUsername, PrintWriter adminOut) {
        Socket userSocket = null;
        
        // Primeiro encontra o socket do usuário
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
        
        // Notifies the user that they are being banned
        try {
            new PrintWriter(userSocket.getOutputStream(), true)
                .println("You have been banned from the server by admin " + adminUsername);
        } catch (IOException e) {
            e.printStackTrace();
        }
        
        // Desconecta o usuário
        try {
            userSocket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        
        adminOut.println("User " + userToBan + " has been banned from the server.");
    }

    private static void muteUser(String userToMute, String adminUsername, PrintWriter adminOut) {
        UserManager.muteUser(userToMute);
        adminOut.println("User " + userToMute + " has been muted.");
        
        // Notifies the user
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
    
    private static void unmuteUser(String userToUnmute, String adminUsername, PrintWriter adminOut) {
        if (UserManager.isUserMuted(userToUnmute)) {
            UserManager.unmuteUser(userToUnmute);
            adminOut.println("User " + userToUnmute + " has been unmuted.");
            
            // Notifies the user
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
    
    private static void promoteUser(String userToPromote, String adminUsername, PrintWriter adminOut) {
        UserManager.promoteToAdmin(userToPromote);
        adminOut.println("User " + userToPromote + " has been promoted to admin by + " + adminUsername + ".");
        
        // Notifies the user
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
    
    private static void demoteUser(String userToDemote, String adminUsername, PrintWriter adminOut) {
        if (UserManager.isAdmin(userToDemote)) {
            UserManager.demoteToUser(userToDemote);
            adminOut.println("User " + userToDemote + " has been demoted to regular user.");
            
            // Notifies the user
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
    
    private static void broadcastAnnouncement(String announcement, String adminUsername) {
        chatRoomsLock.lock();
        try {
            String formattedMessage = "[ANNOUNCEMENT FROM " + adminUsername + "]: " + announcement;
            
            // Broadcast to all rooms
            for (Map.Entry<String, Map<Socket, String>> roomEntry : chatRooms.entrySet()) {
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
    
    private static void deleteRoom(String roomToDelete, String adminUsername, PrintWriter adminOut, String currentRoom) {
        chatRoomsLock.lock();
        try {
            if (!chatRooms.containsKey(roomToDelete)) {
                adminOut.println("Room '" + roomToDelete + "' does not exist.");
                return;
            }
            
            if (roomToDelete.equals("general")) {
                adminOut.println("The 'general' room cannot be deleted.");
                return;
            }
            
            // Notify all users in the room about deletion
            for (Map.Entry<Socket, String> entry : chatRooms.get(roomToDelete).entrySet()) {
                Socket socket = entry.getKey();
                try {
                    new PrintWriter(socket.getOutputStream(), true)
                        .println("This room is being deleted by admin " + adminUsername + ". You will be moved to 'general'.");
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            
            // Move all users to 'general'
            Map<Socket, String> usersToMove = new HashMap<>(chatRooms.get(roomToDelete));
            for (Map.Entry<Socket, String> entry : usersToMove.entrySet()) {
                Socket socket = entry.getKey();
                String username = entry.getValue();
                chatRooms.get("general").put(socket, username);
            }
            
            // Delete the room
            chatRooms.remove(roomToDelete);
            
            adminOut.println("Room '" + roomToDelete + "' has been deleted and all users moved to 'general'.");
            
            // If admin was in the deleted room, move them to general
            if (currentRoom.equals(roomToDelete)) {
                adminOut.println("You have been moved to the 'general' room.");
            }
        } finally {
            chatRoomsLock.unlock();
        }
    }
    
    private static Socket findUserSocketByUsername(String username) {
        clientUsernamesLock.lock();
        try {
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
}

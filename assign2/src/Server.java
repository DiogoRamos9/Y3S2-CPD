import java.net.*;
import java.io.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

public class Server {
    private static final int PORT = 8080;
    private static final String HOST = "0.0.0.0";
    private static ServerSocket serverSocket;

    private static final Map<Socket, String> clientUsernames = new HashMap<>();
    private static final Map<String, Map<Socket, String>> chatRooms = new HashMap<>();
    private static final Map<String, String> aiRoomPrompts = new HashMap<>();
    private static final Map<String, List<String>> aiRoomHistory = new HashMap<>();
    private static final Map<String, Token> userTokens = new HashMap<>();   // username -> token
    private static final Map<String, String> userCurrentRooms = new HashMap<>();
    
    private static final ReentrantLock userRoomsLock = new ReentrantLock();
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
            
            // Get token string from client
            String tokenString = in.readLine();

            Token token;
            boolean isReconnection = false;
            String currentRoom = "general";
            
            // Check if this is a reconnection with valid token
            if (tokenString != null && !tokenString.isEmpty()) {
                token = validateToken(username, tokenString);
                System.out.println("Validating token for " + username + ": " + (token != null ? "VALID" : "INVALID"));
                
                if (token != null) {
                    isReconnection = true;
                    System.out.println("Successful reconnection for " + username + " with token: " + token.getTokenString());
                    
                    // Get the user's current room if reconnecting
                    userRoomsLock.lock();
                    try {
                        if (userCurrentRooms.containsKey(username)) {
                            currentRoom = userCurrentRooms.get(username);
                        }
                    } finally {
                        userRoomsLock.unlock();
                    }
                } else {
                    // Invalid/expired token
                    System.out.println("Expired or invalid token for " + username);
                    out.println("Your session has expired. Please login again.");
                    clientSocket.close();
                    return;
                }
            } else {
                // New connection or no token provided
                User user = UserManager.getUserByUsername(username);
                if (user == null || user.getToken() == null) {
                    // Generate new token for first-time connection
                    token = Token.generateToken(username);
                    userTokens.put(username, token);
                    System.out.println("Generated new token for " + username + ": " + token.getTokenString());
                    
                    User currentUser = UserManager.getUserByUsername(username);
                    if (currentUser != null) {
                        currentUser.setToken(token);
                    }
                } else {
                    // Use existing token
                    token = user.getToken();
                    System.out.println("Using existing token for " + username + ": " + token.getTokenString());
                }
                // Send token to client for future reconnections
                out.println("TOKEN:" + token.getTokenString());
            }

            // Update user tracking
            clientUsernamesLock.lock();
            try {
                clientUsernames.put(clientSocket, username);
            } finally {
                clientUsernamesLock.unlock();
            }

            // Place user in appropriate room
            chatRoomsLock.lock();

            try {
                // If reconnecting, place in last known room
                if (isReconnection) {
                    // Remove from general (default) first to avoid duplication
                    if (chatRooms.containsKey("general")) {
                        chatRooms.get("general").remove(clientSocket);
                    }
                    
                    // Add to current room
                    if (!chatRooms.containsKey(currentRoom)) {
                        chatRooms.put(currentRoom, new HashMap<>());
                    }
                    chatRooms.get(currentRoom).put(clientSocket, username);
                    
                    // Notify user about reconnection
                    out.println("Reconnected to server. You are in room '" + currentRoom + "'.");
                    
                    // Notify other users in the room about reconnection
                    for (Socket socket : chatRooms.get(currentRoom).keySet()) {
                        if (!socket.equals(clientSocket)) {
                            try {
                                new PrintWriter(socket.getOutputStream(), true)
                                    .println(username + " has reconnected to the room.");
                            } catch (IOException e) {
                                // Socket may be closed or broken
                            }
                        }
                    }
                } else {
                    // New connection - place in general room
                    chatRooms.get("general").put(clientSocket, username);
                    
                    // Update current room tracking
                    userRoomsLock.lock();
                    try {
                        userCurrentRooms.put(username, "general");
                    } finally {
                        userRoomsLock.unlock();
                    }
                    out.println("Welcome to the server, " + username + "!");
                    out.println("You are in the 'general' room by default.");
                    out.println("List of commands:");
                    out.println("/create <room_name> - Create a new chat room");
                    out.println("/join <room_name> - Join an existing chat room");
                    out.println("/leave - Leave the current chat room and return to 'general'");
                    out.println("/rooms - List all available chat rooms");
                    out.println("/users - List all user on the current room");
                    out.println("/help - Show this help message");
                    out.println("/disconnect - Disconnect from the server but keep the session active for reconnection");
                    out.println("/exit - Exit the client and terminate the session");
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
                }
            } finally {
                chatRoomsLock.unlock();
            }

            String inputLine;
            while ((inputLine = in.readLine()) != null) {
                // Handle commands
                if (inputLine.startsWith("/")) {
                    if (inputLine.startsWith("/join ")) {
                        String roomName = inputLine.substring(6).trim();
                        
                        // Verificar se a sala existe antes de tentar entrar
                        chatRoomsLock.lock();
                        try {
                            if (!chatRooms.containsKey(roomName)) {
                                // Sala não existe, notificar o usuário
                                out.println("Error: Chat room '" + roomName + "' does not exist.");
                                continue; // Pular para a próxima iteração do loop
                            }
                            
                            // Remover o usuário da sala atual
                            if (chatRooms.containsKey(currentRoom)) {
                                chatRooms.get(currentRoom).remove(clientSocket);
                            }
                            
                            // Adicionar o usuário à nova sala
                            chatRooms.get(roomName).put(clientSocket, username);
                            
                            // Atualizar o rastreamento da sala atual
                            userRoomsLock.lock();
                            try {
                                userCurrentRooms.put(username, roomName);
                            } finally {
                                userRoomsLock.unlock();
                            }
                            
                            currentRoom = roomName;
                            out.println("You joined the room: " + roomName);
                            
                            // Notificar outros usuários na sala
                            for (Socket socket : chatRooms.get(roomName).keySet()) {
                                if (!socket.equals(clientSocket)) {
                                    try {
                                        new PrintWriter(socket.getOutputStream(), true)
                                            .println(username + " has joined the room.");
                                    } catch (IOException e) {
                                        // Socket pode estar fechado ou com problema
                                    }
                                }
                            }
                        } finally {
                            chatRoomsLock.unlock();
                        }
                    }
                    else if (inputLine.equals("/leave")) {
                        userRoomsLock.lock();
                        try {
                            userCurrentRooms.put(username, "general");
                        } finally {
                            userRoomsLock.unlock();
                        }
                        currentRoom = "general";
                    }
                    else if (inputLine.startsWith("/create ")) {
                        String roomSpec = inputLine.substring(8).trim();
                        chatRoomsLock.lock();
                        try {
                            if (chatRooms.containsKey(roomSpec)) {
                                out.println("Chat room already exists.");
                            } else if (roomSpec.startsWith("ai:")) {
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
                    else if (inputLine.equals("/rooms")) {
                        chatRoomsLock.lock();
                        try {
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
                            out.println("Users in the current room (" + currentRoom + "):");
                            for (String user : chatRooms.get(currentRoom).values()) {
                                out.println("- " + user);
                            }
                        } finally {
                            chatRoomsLock.unlock();
                        }
                    }
                    else if (inputLine.equals("/disconnect")) {                        
                        chatRoomsLock.lock();
                        try {
                            if (chatRooms.containsKey(currentRoom)) {
                                for (Socket socket : chatRooms.get(currentRoom).keySet()) {
                                    if (!socket.equals(clientSocket)) {
                                        try {
                                            new PrintWriter(socket.getOutputStream(), true)
                                                .println(username + " has disconnected.");
                                        } catch (IOException e) {}
                                    }
                                }
                                
                                chatRooms.get(currentRoom).remove(clientSocket);
                            }
                        } finally {
                            chatRoomsLock.unlock();
                        }
                        
                        break;
                    }

                    // Admin commands
                    else if (inputLine.startsWith("/ban ")) {
                        if (isAdmin(username)) {
                            String userToBan = inputLine.substring(5).trim();
                            banUser(userToBan, username, out);
                        }
                        else {
                            out.println("You do not have permission to ban users.");
                        }
                    }
                    else if (inputLine.startsWith("/mute ")) {
                        if (isAdmin(username)) {
                            String userToMute = inputLine.substring(6).trim();
                            muteUser(userToMute, username, out);
                        } else {
                            out.println("You do not have permission to mute users.");
                        }
                    }
                    else if (inputLine.startsWith("/unmute ")) {
                        if (isAdmin(username)) {
                            String userToUnmute = inputLine.substring(8).trim();
                            unmuteUser(userToUnmute, username, out);
                        } else {
                            out.println("You do not have permission to unmute users.");
                        }
                    }
                    else if (inputLine.startsWith("/announce ")) {
                        if (isAdmin(username)) {
                            String announcement = inputLine.substring(10).trim();
                            broadcastAnnouncement(announcement, username);
                            out.println("Announcement sent to all rooms.");
                        } else {
                            out.println("You do not have permission to make announcements.");
                        }
                    }
                    else if (inputLine.startsWith("/promote ")) {
                        if (isAdmin(username)) {
                            String userToPromote = inputLine.substring(9).trim();
                            promoteUser(userToPromote, username, out);
                        } else {
                            out.println("You do not have permission to promote users.");
                        }
                    }
                    else if (inputLine.startsWith("/demote ")) {
                        if (isAdmin(username)) {
                            String userToDemote = inputLine.substring(8).trim();
                            demoteUser(userToDemote, username, out);
                        } else {
                            out.println("You do not have permission to demote users.");
                        }
                    }
                    else if (inputLine.equals("/stats")) {
                        if (isAdmin(username)) {
                            displayServerStats(out);
                        } else {
                            out.println("You do not have permission to view server statistics.");
                        }
                    }
                }
                else {
                    boolean isMuted = UserManager.isUserMuted(username);
                    if (isMuted) {
                        out.println("You are currently muted and cannot send messages.");
                        continue;
                    }
                    System.out.println(currentRoom + "/" + username + ": " + inputLine);

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
                        if (aiRoomPrompts.containsKey(currentRoom)) {
                            List<String> history = aiRoomHistory.get(currentRoom);
                            history.add(username + ": " + inputLine);
                            String prompt = aiRoomPrompts.get(currentRoom);
                            String context = prompt + "\n" + String.join("\n", history);
                            String botReply = callLLM(context);
                            history.add("Bot: " + botReply);
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
                String username;
                clientUsernamesLock.lock();
                try {
                    username = clientUsernames.remove(clientSocket);
                } finally {
                    clientUsernamesLock.unlock();
                }
                
                if (username != null) {
                    // Note: We DO NOT remove the user from userCurrentRooms
                    // to maintain their state for reconnection
                    
                    // Also, we don't remove the token in order to allow reconnections
                    System.out.println(username + " disconnected (may reconnect).");
                    
                    // We DO remove them from the active room participants
                    chatRoomsLock.lock();
                    try {
                        // Try to find which room they're in and remove them
                        for (Map<Socket, String> room : chatRooms.values()) {
                            room.remove(clientSocket);
                        }
                    } finally {
                        chatRoomsLock.unlock();
                    }
                }
                clientSocket.close();
            } catch (IOException e) {
                System.out.println("Error in client cleanup: " + e.getMessage());
            }
        }
    }

    private static Token validateToken(String username, String tokenString) {
        // Check if token exists and is valid for this user
        Token token = userTokens.get(username);
        if (token != null && tokenString.equals(token.getTokenString())) {
            if (token.isExpired()) {
                // Token exists but has expired
                userTokens.remove(username);
                return null;
            }
            // Refresh token expiration time when used for reconnection
            token = Token.refreshToken(token);
            userTokens.put(username, token);
            return token;
        }
        return null; // Token doesn't exist or doesn't match
    }

    private static boolean isAdmin(String username) {
        return UserManager.isAdmin(username);
    }

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

    private static String callLLM(String context) {
        try {
            URL url = new URL("http://localhost:11434/api/generate");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);

            String safePrompt = context.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
            String jsonInput = String.format(
                "{ \"model\": \"llama3\", \"prompt\": \"%s\", \"stream\": false }",
                safePrompt
            );

            try (OutputStream os = conn.getOutputStream()) {
                byte[] input = jsonInput.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }

            StringBuilder response = new StringBuilder();
            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) {
                    response.append(line.trim());
                }
            }
            String resp = response.toString();
            int idx = resp.indexOf("\"response\":\"");
            if (idx != -1) {
                int start = idx + 12;
                int end = resp.indexOf("\"", start);
                if (end > start) {
                    return resp.substring(start, end).replace("\\n", "\n");
                }
            }
            return "[AI Error: Unexpected response]";
        } catch (Exception e) {
            e.printStackTrace();
            return "[AI Error: " + e.getMessage() + "]";
        }
    }
}

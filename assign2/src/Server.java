import java.net.*;
import java.io.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.nio.charset.StandardCharsets;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.SSLSocket;

public class Server {
    private static final int PORT = 8080;
    private static final String HOST = "0.0.0.0";
    // private static ServerSocket serverSocket;
    private static SSLServerSocket serverSocket;

    private static final Map<Socket, String> clientUsernames = new HashMap<>();
    private static final Map<String, Map<Socket, String>> chatRooms = new HashMap<>();
    private static final Map<String, String> aiRoomPrompts = new HashMap<>();
    private static final Map<String, List<String>> aiRoomHistory = new HashMap<>();
    private static final Map<String, Token> userTokens = new HashMap<>(); 
    private static final Map<String, String> userCurrentRooms = new HashMap<>();
    private static final Map<String, List<String>> aiRoomBuffer = new ConcurrentHashMap<>();
    private static final Map<String, AtomicBoolean> aiRoomBotBusy = new ConcurrentHashMap<>();
    
    private static final ReentrantLock userRoomsLock = new ReentrantLock();
    private static final ReentrantLock clientUsernamesLock = new ReentrantLock();
    private static final ReentrantLock chatRoomsLock = new ReentrantLock();
    private static final ReentrantLock userTokensLock = new ReentrantLock();
    
    private static final String TOKENS_FILE = "db/tokens.csv";

    public static void main(String[] args) {
        // Set SSL properties (ensure these files exist and are correct)
        System.setProperty("javax.net.ssl.keyStore", "keystore.jks");
        System.setProperty("javax.net.ssl.keyStorePassword", "password123"); 

        UserManager.setupUsers();

        // Load existing tokens from file
        loadTokens();

        try {
            chatRoomsLock.lock();
            try {
                chatRooms.put("general", new HashMap<>());
            } finally {
                chatRoomsLock.unlock();
            }

            SSLServerSocketFactory sslServerSocketFactory = (SSLServerSocketFactory) SSLServerSocketFactory.getDefault();
            serverSocket = (SSLServerSocket) sslServerSocketFactory.createServerSocket(PORT);
            System.out.println("SSL Server started on " + HOST + ":" + PORT);
            while (true) {
                SSLSocket clientSocket = (SSLSocket) serverSocket.accept();
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
                    userTokensLock.lock();
                    try {
                        userTokens.put(username, token);
                    } finally {
                        userTokensLock.unlock();
                    }
                    System.out.println("Generated new token for " + username + ": " + token.getTokenString());
                    
                    User currentUser = UserManager.getUserByUsername(username);
                    if (currentUser != null) {
                        currentUser.setToken(token);
                    }
                    
                    // Save the new token to file
                    saveToken(token);
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
                            } catch (IOException e) {}
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
                    else if (inputLine.equals("/exit")) {
                        // Handled by the client
                        break;
                    }
                    else if (inputLine.equals("/help")) {
                        out.println("List of commands:");
                        out.println("/create <room_name> - Create a new chat room");
                        out.println("/join <room_name> - Join an existing chat room");
                        out.println("/leave - Leave the current chat room and return to 'general'");
                        out.println("/rooms - List all available chat rooms");
                        out.println("/users - List all users in the current room");
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
                    else if (inputLine.equals("/stats")) {
                        if (isAdmin(username)) {
                            displayServerStats(out);
                        } else {
                            out.println("You do not have permission to view server statistics.");
                        }
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
                            // Buffer the message
                            aiRoomBuffer.computeIfAbsent(currentRoom, k -> new ArrayList<>());

                            List<String> buffer = aiRoomBuffer.get(currentRoom);
                            synchronized (buffer) {
                                buffer.add(username + ": " + inputLine);
                            }

                            // If bot is not busy, start processing
                            aiRoomBotBusy.putIfAbsent(currentRoom, new AtomicBoolean(false));
                            AtomicBoolean botBusy = aiRoomBotBusy.get(currentRoom);

                            if (botBusy.compareAndSet(false, true)) {
                                final String roomForBot = currentRoom;
                                Thread.startVirtualThread(() -> processAiRoomBuffer(roomForBot));
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
                    // Get username before removing from clientUsernames
                    username = clientUsernames.get(clientSocket);
                    
                    // IMPORTANT: Don't remove from clientUsernames on disconnect
                    // This allows proper reconnection with the same username
                    if (username != null && !clientSocket.isClosed()) {
                        clientUsernames.put(clientSocket, username);
                    } else {
                        clientUsernames.remove(clientSocket);
                    }
                } finally {
                    clientUsernamesLock.unlock();
                }
                
                if (username != null) {
                    // Note: We do NOT remove the user from userCurrentRooms
                    // to maintain their state for reconnection
                    
                    // Also, we don't remove the token in order to allow reconnections
                    System.out.println(username + " disconnected.");

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

    // Load tokens from file
    private static void loadTokens() {
        File tokenFile = new File(TOKENS_FILE);
        if (!tokenFile.exists()) {
            // Create tokens directory if it doesn't exist
            File tokenDir = new File("db");
            if (!tokenDir.exists()) {
                tokenDir.mkdirs();
            }
            return;
        }

        try (BufferedReader br = new BufferedReader(new FileReader(TOKENS_FILE))) {
            String line;
            while ((line = br.readLine()) != null) {
                // Skip header
                if (line.startsWith("username,token,expiration")) {
                    continue;
                }
                String[] parts = line.split(",");
                if (parts.length == 3) {
                    String username = parts[0];
                    String tokenString = parts[1];
                    Long expiration = Long.parseLong(parts[2]);
                    
                    Token token = new Token(username, tokenString, expiration);
                    
                    // Only load tokens that haven't expired yet
                    if (!token.isExpired()) {
                        userTokensLock.lock();
                        try {
                            userTokens.put(username, token);
                            // Also update the user object
                            User user = UserManager.getUserByUsername(username);
                            if (user != null) {
                                user.setToken(token);
                            }
                            System.out.println("Loaded token for " + username + ": " + tokenString);
                        } finally {
                            userTokensLock.unlock();
                        }
                    }
                    else {
                        System.out.println("WARNING: Token for user " + username + " has expired.");
                    }
                }
            }
            System.out.println("Loaded " + userTokens.size() + " active tokens");
        } catch (IOException e) {
            System.err.println("Error loading tokens: " + e.getMessage());
        }
    }

    // Save token to file
    private static void saveToken(Token token) {
        try {
            // Create directory if it doesn't exist
            File tokenDir = new File("db");
            if (!tokenDir.exists()) {
                tokenDir.mkdirs();
            }
            
            // Append the token to the file
            boolean fileExists = new File(TOKENS_FILE).exists();
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(TOKENS_FILE, true))) {
                if (!fileExists) {
                    // Create the file with a header if it doesn't exist
                    writer.write("username,token,expiration\n");
                }
                
                writer.write(token.getUsername() + "," + 
                             token.getTokenString() + "," + 
                             token.getExpirationTime() + "\n");
            }
        } catch (IOException e) {
            System.err.println("Error saving token: " + e.getMessage());
        }
    }

    // Method to update/rewrite all tokens to file (for refreshed tokens)
    private static void updateTokensFile() {
        userTokensLock.lock();
        try {
            // Create directory if it doesn't exist
            File tokenDir = new File("db");
            if (!tokenDir.exists()) {
                tokenDir.mkdirs();
            }
            
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(TOKENS_FILE, false))) {
                // Write header
                writer.write("username,token,expiration\n");
                
                // Write all tokens
                for (Token token : userTokens.values()) {
                    if (!token.isExpired()) {
                        writer.write(token.getUsername() + "," + 
                                     token.getTokenString() + "," + 
                                     token.getExpirationTime() + "\n");
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("Error updating tokens file: " + e.getMessage());
        } finally {
            userTokensLock.unlock();
        }
    }

    private static Token validateToken(String username, String tokenString) {
        // Check if token exists and is valid for this user
        userTokensLock.lock();
        try {
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
                
                // Update token in file system
                updateTokensFile();
                
                return token;
            }
        } finally {
            userTokensLock.unlock();
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

    private static void processAiRoomBuffer(String roomName) {
        while (true) {
            List<String> buffer = aiRoomBuffer.get(roomName);
            if (buffer == null) {
                aiRoomBotBusy.get(roomName).set(false);
                return;
            }
            List<String> toSend;
            synchronized (buffer) {
                if (buffer.isEmpty()) {
                    aiRoomBotBusy.get(roomName).set(false);

                    if (buffer.isEmpty()) {
                        return;
                    }
                    if (aiRoomBotBusy.get(roomName).compareAndSet(false, true)) {
                        continue;
                    } else {
                        return;
                    }
                }
                toSend = new ArrayList<>(buffer);
                buffer.clear();
            }

            String prompt = aiRoomPrompts.get(roomName);
            String context = prompt + "\n" + String.join("\n", toSend);
            String botReply = callLLM(context);

            aiRoomHistory.get(roomName).addAll(toSend);
            aiRoomHistory.get(roomName).add("Bot: " + botReply);

            chatRoomsLock.lock();
            try {
                for (Socket socket : chatRooms.get(roomName).keySet()) {
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
            } finally {
                chatRoomsLock.unlock();
            }
        }
    }
}

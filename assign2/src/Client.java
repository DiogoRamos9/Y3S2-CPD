import java.net.*;
import java.io.*;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

public class Client {
    private static final int PORT = 8080;
    private static final String HOST = "127.0.0.1";
    private static SSLSocket socket;
    private static boolean running = true;
    private static boolean connected = false;
    private static boolean isExit = false;
    private static boolean voluntaryDisconnect = false;
    private static BufferedReader in;
    private static PrintWriter out;
    private static User user;
    private static String tokenString = "";
    private static String currentRoom = "general"; 
    private static final int RECONNECT_DELAY_MS = 2000;
    private static final int MAX_RECONNECT_ATTEMPTS = 5;
    
    public static void main(String[] args) {
        authenticateUser();
        
        int reconnectAttempts = 0;
        while (running && reconnectAttempts < MAX_RECONNECT_ATTEMPTS) {
            try {
                if (!connected) {
                    connectToServer();
                    connected = true;
                }
                
                // Reset reconnect attempts on successful connection
                reconnectAttempts = 0;
                
                // Main processing loop
                BufferedReader userInput = new BufferedReader(new InputStreamReader(System.in));
                System.out.print(user.getUsername() + ": ");
                String message;
                while (running && connected && socket != null && socket.isConnected() && !socket.isClosed() && (message = userInput.readLine()) != null) {
                    if (message.isEmpty()) {
                        continue;
                    }
                    
                    boolean isCommand = message.charAt(0) == '/';
                    
                    if (isCommand) {
                        handleCommand(message);
                        if (!running) {
                            break;
                        }
                        // Track room changes
                        if (message.startsWith("/join ")) {
                            currentRoom = message.substring(6).trim();
                        } else if (message.equals("/leave")) {
                            currentRoom = "general";
                        } else if (message.equals("/disconnect")) {
                            disconnectFromServer();
                            break;
                        }
                    } else {
                        System.out.println("\r" + user.getUsername() + ": " + message);
                    }

                    if (connected && out != null) {
                        // Send the message to the server
                        out.println(message);
                        System.out.print(user.getUsername() + ": ");
                    }
                }

                if (!connected && running) {
                    String input = userInput.readLine();
                    if ("quit".equalsIgnoreCase(input)) {
                        running = false;
                    }
                }
            } catch (IOException e) {
                System.out.println("\rConnection lost. Attempting to reconnect in " + (RECONNECT_DELAY_MS/1000) + " seconds...");
                reconnectAttempts++;
                
                if (reconnectAttempts < MAX_RECONNECT_ATTEMPTS) {
                    try {
                        Thread.sleep(RECONNECT_DELAY_MS);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                    }
                    System.out.println("Reconnect attempt " + reconnectAttempts + " of " + MAX_RECONNECT_ATTEMPTS + "...");
                } else {
                    System.out.println("Failed to reconnect after " + MAX_RECONNECT_ATTEMPTS + " attempts. Exiting.");
                    running = false;
                    return;
                }
            }
        }
        
        System.out.println("Exiting program.");
    }

    private static void connectToServer() throws IOException {
        // Set trust store properties (for self-signed certificates)
        System.setProperty("javax.net.ssl.trustStore", "truststore.jks");
        System.setProperty("javax.net.ssl.trustStorePassword", "password123");

        // Close previous socket if exists
        if (socket != null && !socket.isClosed()) {
            try {
                socket.close();
            } catch (IOException e) {}
        }

        SSLSocketFactory sslSocketFactory = (SSLSocketFactory) SSLSocketFactory.getDefault();
        socket = (SSLSocket) sslSocketFactory.createSocket(HOST, PORT);
        System.out.println("Connected to server at " + HOST + ":" + PORT);

        in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        out = new PrintWriter(socket.getOutputStream(), true);

        voluntaryDisconnect = false;

        // Send username and token for authentication/reconnection
        out.println(user.getUsername());
        out.println(tokenString); // Send token string (empty on first login)
        
        // Start listening for server messages
        Thread serverListener = new Thread(() -> {
            try {
                String serverMessage;
                while (connected && (serverMessage = in.readLine()) != null) {
                    // If we receive a token from server, save it
                    if (serverMessage.startsWith("TOKEN:")) {
                        tokenString = serverMessage.substring(6);
                        user.setToken(Token.getToken(user.getUsername(), tokenString));
                        continue;
                    }

                    if (serverMessage.startsWith("BANNED:")) {                        
                        connected = false;
                        running = false;
                        voluntaryDisconnect = true;
                        
                        try {
                            if (socket != null && !socket.isClosed()) {
                                socket.close();
                            }
                        } catch (IOException e) {}
                        
                        new Thread(() -> {
                            try {
                                System.exit(0);  
                            } catch (Exception e) {
                                System.out.println("Error during exit: " + e.getMessage());
                            }
                        }).start();
                        
                        return;
                    }

                    // If we receive a message indicating the user is banned, handle it
                    if (serverMessage.startsWith("ROLE_UPDATE:")) {
                        String newRole = serverMessage.substring(12);
                        user.setRole(newRole);
                        System.out.println("\rYour role has been updated to: " + newRole);
                        System.out.print(user.getUsername() + ": ");
                        continue; 
                    }
                    
                    // Handle session expiration
                    if (serverMessage.equals("Your session has expired. Please login again.")) {
                        // Clear the token so we'll login again on next connection
                        tokenString = "";
                        if (user.getToken() != null) {
                            user.setToken(null);
                        }
                        
                        System.out.println(serverMessage);
                        // Force reconnection with new authentication
                        disconnectFromServer();
                        try {
                            authenticateUser(); // Re-authenticate the user
                            connectToServer();  // And reconnect
                            connected = true;
                        } catch (IOException e) {
                            System.out.println("Failed to reconnect after session expiration.");
                        }
                        continue;
                    }
                    
                    // Capture room join confirmations to update current room
                    if (serverMessage.startsWith("You joined the room: ")) {
                        currentRoom = serverMessage.substring("You joined the room: ".length());
                    }
                    
                    System.out.print("\r");  // Clear line
                    System.out.println(serverMessage);
                    System.out.print(user.getUsername() + ": ");
                }
                                
                // Only try to reconnect if this was not a voluntary disconnect
                if (connected && !voluntaryDisconnect) {
                    System.out.println("\rServer disconnected. Attempting to reconnect...");
                    connected = false;
                    
                    // Connection attempt
                    boolean reconnected = false;
                    for (int attempts = 1; attempts <= MAX_RECONNECT_ATTEMPTS; attempts++) {
                        System.out.println("Reconnect attempt " + attempts + " of " + MAX_RECONNECT_ATTEMPTS + "...");
                        try {
                            Thread.sleep(RECONNECT_DELAY_MS);
                            connectToServer(); // Use the existing method to properly reconnect
                            System.out.println("Reconnected successfully!");
                            reconnected = true;
                            connected = true; // Set connected flag
                            break;
                        } catch (IOException | InterruptedException e) {}
                    }
                    
                    if (!reconnected) {
                        System.out.println("Failed to reconnect after " + MAX_RECONNECT_ATTEMPTS + " attempts. Exiting.");
                        running = false;
                    }
                }
            } catch (IOException e) {
                // Server disconnected unexpectedly
                if (running && !voluntaryDisconnect) {
                    System.out.println("\rConnection lost. Attempting to reconnect...");
                    connected = false;
                    
                    // Attempt to reconnect
                    boolean reconnected = false;
                    for (int attempts = 1; attempts <= MAX_RECONNECT_ATTEMPTS; attempts++) {
                        System.out.println("Reconnect attempt " + attempts + " of " + MAX_RECONNECT_ATTEMPTS + "...");
                        try {
                            Thread.sleep(RECONNECT_DELAY_MS);
                            connectToServer(); // Use the existing method to properly reconnect
                            System.out.println("Reconnected successfully!");
                            reconnected = true;
                            connected = true; // Set connected flag
                            break;
                        } 
                        catch (IOException ex) {}
                        catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                        }
                    }
                    
                    if (!reconnected) {
                        System.out.println("Failed to reconnect after " + MAX_RECONNECT_ATTEMPTS + " attempts. Exiting.");
                        running = false;
                    }
                }
            }
        });
        serverListener.setDaemon(true);
        serverListener.start();
    }

    private static void disconnectFromServer() {
        // Set voluntary disconnect flag to prevent auto-reconnection
        voluntaryDisconnect = true;
        
        if (connected && out != null) {
            out.println("/disconnect");  // Inform server about voluntary disconnection
        }
        
        connected = false;
        
        if (socket != null) {
            try {
                socket.close();
            } catch (IOException e) {}
        }
        
        if (!isExit) {
            System.out.println("Disconnected from server. Type anything to reconnect or 'quit' to exit.");
        }
        else {
            isExit = false;
        }
        
    }

    private static void handleCommand(String command) {
        String[] parts = command.trim().split("\\s+", 2);
        String cmd = parts[0];
        switch (cmd) {
            case "/disconnect":
                disconnectFromServer();
                break;
            
            case "/exit":
                running = false;
                isExit = true;
                disconnectFromServer();
                break;
            
            case "/help":
                System.out.println("Available commands:");
                System.out.println("/create <room_name> - Create a new chat room");
                System.out.println("/create ai:<room_name>:<room_theme> - Create a new chat room with an AI chatbot");
                System.out.println("/join <room_name> - Join an existing chat room");
                System.out.println("/leave - Leave the current chat room and return to 'general'");
                System.out.println("/rooms - List all available chat rooms");
                System.out.println("/users - List all users in the current room");
                System.out.println("/help - Show this help message");
                System.out.println("/status - Show the current status of the client");
                System.out.println("/disconnect - Disconnect from server but keep program running");
                System.out.println("/exit - Exit the client program");
                if (user.getRole().equals("admin")) {
                    System.out.println("/ban <username> - Ban a user from the server");
                    System.out.println("/mute <username> - Temporarily prevent a user from sending messages");
                    System.out.println("/unmute <username> - Allow a muted user to send messages again");
                    System.out.println("/announce <message> - Send an announcement to all chat rooms");
                    System.out.println("/promote <username> - Promote a user to admin role");
                    System.out.println("/demote <username> - Demote an admin to regular user");
                    System.out.println("/stats - Show server statistics and active connections");
                }
                break;
            
            // These commands are handled by the server
            case "/create": case "/join": case "/leave": case "/rooms": case "/users": case "/kick": case "/ban": case "/delete": case "/mute": case "/unmute": case "/announce": case "/promote": case "/demote": case "/stats":
                break;

            case "/status":
                System.out.println("Current user: " + user.getUsername());
                if (user.getRole().equals("admin")) {
                    System.out.println("Role: Admin");
                } else {
                    System.out.println("Role: User");
                }
                System.out.println("Current room: " + currentRoom);
                System.out.println("Connected: " + connected);
                if (user.getToken() == null) {
                    System.out.println("No token available.");
                } else {
                    System.out.println("Token: " + user.getToken().getTokenString());
                    if (user.getToken().isExpired()) {
                        System.out.println("Token status: Expired");
                    }
                }
                break;

            default:
                System.out.println("Unknown command: " + cmd);
        }
    }

    private static void authenticateUser() {
        boolean authenticated = false;
        
        if (user != null && !tokenString.isEmpty()) {
            System.out.println("Using saved authentication token.");
            return;
        }

        while (!authenticated) {
            System.out.println("1 - Login \n2 - Register");
            String choice = System.console().readLine();
            switch (choice) {
                case "1":
                    if ((user = User.loginUser()) == null) {
                        continue;
                    } else {
                        System.out.println("Login successful!");
                        authenticated = true;
                    }
                    break;
                case "2":
                    if ((user = User.registerUser()) == null) {
                        System.out.println("Registration failed. Please try again.");
                    } else {
                        System.out.println("Registration successful!");
                        authenticated = true;
                    }
                    break;
                default:
                    System.out.println("Invalid option. Please try again.");
                    break;
            }
        }
    }
}
import java.net.*;
import java.io.*;

public class Client {
    private static final int PORT = 8080;
    private static final String HOST = "127.0.0.1";
    private static Socket socket;
    private static boolean running = true;
    private static boolean connected = false;
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
                }
            }
        }
        
        System.out.println("Exiting program.");
    }

    private static void connectToServer() throws IOException {
        // Close previous socket if exists
        if (socket != null && !socket.isClosed()) {
            try {
                socket.close();
            } catch (IOException e) {}
        }
        
        socket = new Socket(HOST, PORT);
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
                            Socket newSocket = new Socket(HOST, PORT);
                            System.out.println("Reconnected successfully!");
                            reconnected = true;
                            socket = newSocket;
                            break;
                        } catch (IOException | InterruptedException e) {
                            System.out.println("Reconnect attempt " + attempts + " failed.");
                        }
                    }
                    
                    if (!reconnected) {
                        System.out.println("Failed to reconnect after " + MAX_RECONNECT_ATTEMPTS + " attempts. Exiting.");
                        running = false;
                    }
                }
            } catch (IOException e) {
                // Server disconnected unexpectedly
                if (running && !voluntaryDisconnect) {
                    System.out.println("\rConnection lost HERE. Attempting to reconnect...");
                    connected = false;
                    
                    // Attempt to reconnect
                    boolean reconnected = false;
                    for (int attempts = 1; attempts <= MAX_RECONNECT_ATTEMPTS; attempts++) {
                        System.out.println("Reconnect attempt " + attempts + " of " + MAX_RECONNECT_ATTEMPTS + "...");
                        try {
                            Thread.sleep(RECONNECT_DELAY_MS);
                            Socket newSocket = new Socket(HOST, PORT);
                            System.out.println("Reconnected successfully!");
                            reconnected = true;
                            socket = newSocket;                      
                            break;
                        } catch (IOException ex) {
                            System.out.println("Reconnect attempt " + attempts + " failed.");
                        } catch (InterruptedException ie) {
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
        
        System.out.println("Disconnected from server. Type anything to reconnect or 'quit' to exit.");
    }

    private static void handleCommand(String command) {
        String[] parts = command.trim().split("\\s+", 2);
        String cmd = parts[0];
        switch (cmd) {
            case "/quit":
                running = false;
                break;
            
            case "/disconnect":
                disconnectFromServer();
                break;
            
            case "/exit":
                running = false;
                disconnectFromServer();
                break;
            
            case "/help":
                System.out.println("Available commands:");
                System.out.println("/create <room_name> - Create a new chat room");
                System.out.println("/join <room_name> - Join an existing chat room");
                System.out.println("/leave - Leave the current chat room and return to 'general'");
                System.out.println("/rooms - List all available chat rooms");
                System.out.println("/users - List all users in the current room");
                System.out.println("/help - Show this help message");
                System.out.println("/status - Show the current status of the client");
                System.out.println("/disconnect - Disconnect from server but keep program running");
                System.out.println("/exit - Exit the client program");
                System.out.println("/quit - Exit the client program (same as /exit)");
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
                System.out.println("Current room: " + currentRoom);
                System.out.println("Connected: " + connected);
                System.out.println("Token: " + (tokenString.isEmpty() ? "None" : "Valid"));
                break;

            default:
                System.out.println("Unknown command: " + cmd);
        }
    }

    private static void authenticateUser() {
        boolean authenticated = false;
        
        if (user != null && !tokenString.isEmpty()) {
            authenticated = true;
            System.out.println("Using saved authentication token.");
            return;
        }

        if (!authenticated) {
            do {
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
            } while (!authenticated);
        }
    }
}
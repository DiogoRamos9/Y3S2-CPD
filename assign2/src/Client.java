import java.net.*;
import java.io.*;

public class Client {
    private static final int PORT = 8080;
    private static final String HOST = "127.0.0.1";
    private static Socket socket;
    private static boolean running = true;
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
                connectToServer();
                
                // Reset reconnect attempts on successful connection
                reconnectAttempts = 0;
                
                // Main processing loop
                BufferedReader userInput = new BufferedReader(new InputStreamReader(System.in));
                System.out.print(user.getUsername() + ": ");
                String message;
                while (running && socket.isConnected() && !socket.isClosed() && (message = userInput.readLine()) != null) {
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
                        }
                    } else {
                        System.out.println("\r" + user.getUsername() + ": " + message);
                    }
                    
                    out.println(message);
                    System.out.print(user.getUsername() + ": ");
                }
            } catch (IOException e) {
                System.out.println("Connection lost. Attempting to reconnect...");
                reconnectAttempts++;
                
                if (reconnectAttempts < MAX_RECONNECT_ATTEMPTS) {
                    try {
                        Thread.sleep(RECONNECT_DELAY_MS);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                    }
                } else {
                    System.out.println("Failed to reconnect after " + MAX_RECONNECT_ATTEMPTS + " attempts. Exiting.");
                }
            }
        }
        
        System.out.println("Disconnected from server.");
    }

    private static void connectToServer() throws IOException {
        // Close previous socket if exists
        if (socket != null && !socket.isClosed()) {
            try {
                socket.close();
            } catch (IOException e) {
                // Ignore, we're already handling a connection problem
            }
        }
        
        socket = new Socket(HOST, PORT);
        System.out.println("Connected to server at " + HOST + ":" + PORT);
        
        in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        out = new PrintWriter(socket.getOutputStream(), true);
        
        // Send username and token for authentication/reconnection
        out.println(user.getUsername());
        out.println(tokenString); // Send token string (empty on first login)
        
        // Start listening for server messages
        Thread serverListener = new Thread(() -> {
            try {
                String serverMessage;
                while ((serverMessage = in.readLine()) != null) {
                    // If we receive a token from server, save it
                    if (serverMessage.startsWith("TOKEN:")) {
                        tokenString = serverMessage.substring(6);
                        System.out.println("Received authentication token from server.");
                        continue;
                    }
                    
                    System.out.print("\r");  // Clear line
                    System.out.println(serverMessage);
                    System.out.print(user.getUsername() + ": ");
                }
            } catch (IOException e) {
                if (running) {
                    System.out.println("\rConnection to server lost.");
                }
            }
        });
        serverListener.setDaemon(true);
        serverListener.start();
    }

    private static void handleCommand(String command) {
        String[] parts = command.trim().split("\\s+", 2);
        command = parts[0];
        switch (command) {
            case "/exit":
                out.println("Client disconnected.");
                running = false;
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
                System.out.println("/exit - Exit the client");
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
            case "/create": case "/join": case "/leave": case "/rooms": case "/users": case "/kick": case "/ban": case "/delete": case "/mute": case "/unmute": case "/announce": case "/promote": case "/demote": case "/stats":
                break;

            case "/status":
                System.out.println("Current user: " + user.getUsername());
                System.out.println("Current room: " + currentRoom);
                System.out.println("Token: " + tokenString);
                break;

            default:
                System.out.println("Unknown command: " + command);
        }
    }

    private static void authenticateUser() {
        boolean authenticated = false;
        
        // TODO: Implement a method to check if the token is valid

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
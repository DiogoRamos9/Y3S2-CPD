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

    public static void main(String[] args) {
        authenticateUser();
    
        try {
            socket = new Socket(HOST, PORT);
            System.out.println("Connected to server at " + HOST + ":" + PORT);
    
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);
    
            // Envia o nome do client para o servidor
            out.println(user.getUsername());
    
            // Thread to listen for messages from the server
            Thread serverListener = new Thread(() -> {
                try {
                    String serverMessage;
                    while ((serverMessage = in.readLine()) != null) {
                        // Clears the current line and prints the server message
                        System.out.print("\r");  
                        System.out.println(serverMessage);  
                        System.out.print(user.getUsername() + ": ");
                    }
                } catch (IOException e) {}
            });
            serverListener.start();
    
            BufferedReader userInput = new BufferedReader(new InputStreamReader(System.in));
            System.out.print(user.getUsername() + ": ");
            while (running && socket.isConnected() && !socket.isClosed()) {
                // Read user input
                String message = userInput.readLine();
                
                if (message == null || message.isEmpty()) {
                    continue;
                }
                
                Boolean isCommand = message.charAt(0) == '/';
                
                if (isCommand) {
                    handleCommand(message);
                    if (!running) {
                        break;
                    }
                }
                
                else {
                    System.out.println("\r" + user.getUsername() + ": " + message);
                }
                
                out.println(message);
                
                System.out.print(user.getUsername() + ": ");
            }
    
            socket.close();
            System.out.println("Disconnected from server.");
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (socket != null && !socket.isClosed()) {
                try {
                    socket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
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
                System.out.println("/exit - Exit the client");
                System.out.println("/create <room_name> - Create a new chat room");
                System.out.println("/join <room_name> - Join an existing chat room");
                System.out.println("/leave - Leave the current chat room");
                System.out.println("/list - List all available chat rooms");
                System.out.println("/help - Show this help message");
                break;
            case "/create": case "/join": case "/leave": case "/list":
                break;

            default:
                System.out.println("Unknown command: " + command);
        }
    }

    private static void authenticateUser() {
        boolean authenticated = false;
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
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
    
            // Thread to listen for messages from the server
            Thread serverListener = new Thread(() -> {
                try {
                    String serverMessage;
                    while ((serverMessage = in.readLine()) != null) {
                        System.out.println("\nServer: " + serverMessage);
                        System.out.print(user.getUsername() + ": ");
                    }
                } catch (IOException e) {}
            });
            serverListener.start();
    
            BufferedReader userInput = new BufferedReader(new InputStreamReader(System.in));
            while (running && socket.isConnected() && !socket.isClosed()) {
                System.out.print(user.getUsername() + ": ");
                String message = userInput.readLine();
    
                if (message.charAt(0) == '/') {
                    handleCommand(message);
                    if (!running) {
                        break;
                    }
                }
    
                out.println(message);
    
                System.out.println(user.getUsername() + ": " + message);
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
        switch (command) {
            case "/exit":
                out.println("Client disconnected.");
                running = false;
                break;
            case "/help":
                System.out.println("Available commands:");
                System.out.println("/exit - Exit the client");
                System.out.println("/help - Show this help message");
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
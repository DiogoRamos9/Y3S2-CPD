import java.net.*;
import java.io.*;

public class Client {
    private static final int PORT = 8080;
    private static final String HOST = "localhost";
    private static Socket socket;
    private static boolean running = true;
    private static BufferedReader in;
    private static PrintWriter out;

    public static void main(String[] args) {
        try {
            socket = new Socket(HOST, PORT);
            System.out.println("Connected to server at " + HOST + ":" + PORT);

            while (running && socket.isConnected() && !socket.isClosed()) {
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                out = new PrintWriter(socket.getOutputStream(), true);

                // Read welcome message from server
                String welcomeMessage = in.readLine();
                System.out.println("Server: " + welcomeMessage);

                // Create a reader for user input
                BufferedReader userInput = new BufferedReader(new InputStreamReader(System.in));

                while (true) {
                    // Prompt user for input
                    System.out.print("You: ");
                    String message = userInput.readLine();

                    // Check if user wrote a command
                    if (message.charAt(0) == '/') {
                        handleCommand(message);
                        if (!running) {
                            break;
                        }
                    }

                    // Send message to server
                    out.println(message);
                }

                socket.close();
                System.out.println("Disconnected from server.");
            }
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
                out.println("Client disconnected: " + socket.getInetAddress());
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
}
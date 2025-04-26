import java.net.*;
import java.io.*;

public class Client {
    private static final int PORT = 8080;
    private static final String HOST = "localhost";
    private static Socket socket;

    public static void main(String[] args) {
        try {
            socket = new Socket(HOST, PORT);
            System.out.println("Connected to server at " + HOST + ":" + PORT);

            boolean running = true;

            while (running && socket.isConnected() && !socket.isClosed()) {
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                PrintWriter out = new PrintWriter(socket.getOutputStream(), true);

                // Read welcome message from server
                String welcomeMessage = in.readLine();
                System.out.println("Server: " + welcomeMessage);

                // Create a reader for user input
                BufferedReader userInput = new BufferedReader(new InputStreamReader(System.in));

                while (true) {
                    // Prompt user for input
                    System.out.print("You: ");
                    String message = userInput.readLine();

                    // Check if user wants to exit
                    if (message.equalsIgnoreCase("esc")) {
                        System.out.println("Exiting...");
                        running = false;
                        break;
                    }

                    // Send message to server
                    out.println(message);
                }
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
}
import java.net.*;
import java.io.*;

public class Server {
    private static final int PORT = 8080;
    private static final String HOST = "localhost";

    public static void main(String[] args) {
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("Server started on " + HOST + ":" + PORT);
            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("Client connected: " + clientSocket.getInetAddress());
                
                // Handle client connection in a separate thread to allow multiple clients
                // to connect simultaneously and to avoid blocking the main thread.
                new Thread(() -> handleClient(clientSocket)).start();

                if (clientSocket.isClosed() || !clientSocket.isConnected()) {
                    System.out.println("Client disconnected: " + clientSocket.getInetAddress());
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void handleClient(Socket clientSocket) {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
             PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true)) {
            out.println("Welcome to the server!");
            String inputLine;
            while ((inputLine = in.readLine()) != null) {
                System.out.println("Received: " + inputLine);
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                clientSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
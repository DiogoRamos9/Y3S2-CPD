import java.net.*;
import java.io.*;
import java.util.concurrent.ConcurrentHashMap;

public class Server {
    private static final int PORT = 8080;
    private static final String HOST = "0.0.0.0"; // Accept connections from any IP address
    private static ServerSocket serverSocket;
    private static ConcurrentHashMap<Socket, String> clientUsernames = new ConcurrentHashMap<>();

    public static void main(String[] args) {
        try {
            serverSocket = new ServerSocket(PORT);
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

            // Lê o username do client e o armazena no mapa
            String username = in.readLine();
            clientUsernames.put(clientSocket, username);
            System.out.println(username + " has joined the server.");

            out.println("Welcome to the server, " + username + "!");
            String inputLine;
            while ((inputLine = in.readLine()) != null) {
                System.out.println(username + ": " + inputLine);
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                String username = clientUsernames.remove(clientSocket);
                if (username != null) {
                    System.out.println(username + " has disconnected.");
                }
                clientSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
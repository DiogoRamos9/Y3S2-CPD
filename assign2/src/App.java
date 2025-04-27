public class App {
    public static void main(String[] args) {
        if (args.length != 1 || (!args[0].equals("server") && !args[0].equals("client"))) {
            System.out.println("Usage: java --enable-preview App <server|client>");
            return;
        }

        UserManager.setupUsers(); // Initialize users before starting the server or client

        if (args[0].equals("server")) {
            Server.main(args);
        }
        
        else if (args[0].equals("client")) {
            Client.main(args);
        }
    }
}

import java.util.ArrayList;
import java.util.List;
import java.io.*;

public class UserManager {
    public static List<User> users = new ArrayList<>();

    public static void addUser(String username, String password) {
        User user = new User(username, password);
        users.add(user);
    }

    public static void setupUsers() {
        try (BufferedReader br = new BufferedReader(new FileReader("db/users.csv"))) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] parts = line.split(",");
                if (parts.length == 2) {
                    String username = parts[0].trim();
                    String password = parts[1].trim();
                    addUser(username, password);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

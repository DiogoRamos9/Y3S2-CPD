import java.io.*;

public class User {
    private String username;
    private String password;

    User(String username, String password) {
        this.username = username;
        this.password = password;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public static int loginUser() {
        System.out.println("Enter username: ");
        String username = System.console().readLine();
        for (User user : UserManager.users) {
            if (user.getUsername().equals(username)) {
                System.out.println("Enter password: ");
                String password = System.console().readLine();
                if (user.getPassword().equals(password)) {
                    return 0;
                } else {
                    System.out.println("Incorrect password.");
                    return 1;
                }
            }
        }
        System.out.println("User not found.");
        return 1;
    }

    public static int registerUser() {
        System.out.println("Enter username: ");
        String username = System.console().readLine();
        for (User user : UserManager.users) {
            if (user.getUsername().equals(username)) {
                System.out.println("Username already exists.");
                return 1;
            }
        }
        System.out.println("Enter password: ");
        String password = System.console().readLine();
        UserManager.addUser(username, password);
        try (BufferedWriter bw = new BufferedWriter(new FileWriter("db/users.csv", true))) {
            bw.write(username + "," + password);
            bw.newLine();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return 0;
    }
}
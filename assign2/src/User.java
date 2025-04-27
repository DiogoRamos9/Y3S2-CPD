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

    public static User loginUser() {
        System.out.println("Enter username: ");
        String username = System.console().readLine();
        for (User user : UserManager.users) {
            if (user.getUsername().equals(username)) {
                System.out.println("Enter password: ");
                String password = System.console().readLine();
                if (user.getPassword().equals(password)) {
                    return user;
                } else {
                    System.out.println("Incorrect password.");
                    return null;
                }
            }
        }
        System.out.println("User not found.");
        return null;
    }

    public static User registerUser() {
        System.out.println("Enter username: ");
        String username = System.console().readLine();
        for (User user : UserManager.users) {
            if (user.getUsername().equals(username)) {
                System.out.println("Username already exists.");
                return null;
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

        User user = new User(username, password);
        return user;
    }
}
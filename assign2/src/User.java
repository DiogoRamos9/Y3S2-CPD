import java.io.*;

public class User {
    private String username;
    private String password;
    private String role;
    private Token token;

    User(String username, String password, String role) {
        this.username = username;
        this.password = password;
        this.role = role;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public void setToken(Token token) {
        this.token = token;
    }

    public Token getToken() {
        return token;
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
        UserManager.addUser(username, password, "user");
        try (BufferedWriter bw = new BufferedWriter(new FileWriter("db/users.csv", true))) {
            bw.write(username + "," + password + ",user");
            bw.newLine();
        } catch (IOException e) {
            e.printStackTrace();
        }

        User user = new User(username, password, "user");
        return user;
    }
}
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.io.*;
import java.util.concurrent.locks.ReentrantLock;

public class UserManager {
    public static List<User> users = new ArrayList<>();
    private static Map<String, Boolean> mutedUsers = new HashMap<>();
    private static Map<String, String> userRoles = new HashMap<>();
    
    private static final ReentrantLock usersLock = new ReentrantLock();
    private static final ReentrantLock mutedUsersLock = new ReentrantLock();
    private static final ReentrantLock userRolesLock = new ReentrantLock();

    public static void addUser(String username, String password, String role) {
        User user = new User(username, password, role);
        usersLock.lock();
        try {
            users.add(user);
            userRoles.put(username, role);
        } finally {
            usersLock.unlock();
        }
    }

    public static boolean isUserMuted(String username) {
        mutedUsersLock.lock();
        try {
            return mutedUsers.getOrDefault(username, false);
        } finally {
            mutedUsersLock.unlock();
        }
    }

    public static void muteUser(String username) {
        mutedUsersLock.lock();
        try {
            mutedUsers.put(username, true);
        } finally {
            mutedUsersLock.unlock();
        }
    }

    public static void unmuteUser(String username) {
        mutedUsersLock.lock();
        try {
            mutedUsers.put(username, false);
        } finally {
            mutedUsersLock.unlock();
        }
    }

    public static boolean isAdmin(String username) {
        userRolesLock.lock();
        try {
            return "admin".equals(userRoles.getOrDefault(username, "user"));
        } finally {
            userRolesLock.unlock();
        }
    }

    public static void promoteToAdmin(String username) {
        userRolesLock.lock();
        try {
            userRoles.put(username, "admin");
            
            usersLock.lock();
            try {
                for (User user : users) {
                    if (user.getUsername().equals(username)) {
                        user.setRole("admin");
                        break;
                    }
                }
            } finally {
                usersLock.unlock();
            }
        } finally {
            userRolesLock.unlock();
        }
    }

    public static void demoteToUser(String username) {
        userRolesLock.lock();
        try {
            userRoles.put(username, "user");
            
            // Atualiza também no objeto User, se existir
            usersLock.lock();
            try {
                for (User user : users) {
                    if (user.getUsername().equals(username)) {
                        user.setRole(username);
                        break;
                    }
                }
            } finally {
                usersLock.unlock();
            }
        } finally {
            userRolesLock.unlock();
        }
    }

    public static User getUserByUsername(String username) {
        usersLock.lock();
        try {
            for (User user : users) {
                if (user.getUsername().equals(username)) {
                    return user;
                }
            }
            return null;
        } finally {
            usersLock.unlock();
        }
    }

    public static List<String> getMutedUsersList() {
        List<String> mutedList = new ArrayList<>();
        mutedUsersLock.lock();
        try {
            for (Map.Entry<String, Boolean> entry : mutedUsers.entrySet()) {
                if (entry.getValue()) {
                    mutedList.add(entry.getKey());
                }
            }
        } finally {
            mutedUsersLock.unlock();
        }
        return mutedList;
    }

    public static void setupUsers() {
        try (BufferedReader br = new BufferedReader(new FileReader("db/users.csv"))) {
            String line;
            while ((line = br.readLine()) != null) {
                // Skip header
                if (line.startsWith("username,password,role")) {
                    continue;
                }
                String[] parts = line.split(",");
                if (parts.length == 3) {
                    String username = parts[0].trim();
                    String password = parts[1].trim();
                    String role = parts[2].trim();
                    if (role.equals("admin") || role.equals("user")) {
                        addUser(username, password, role);
                    } else {
                        System.out.println("Invalid role for user: " + username);
                    }
                }
            }
        } catch (IOException e) {
            System.out.println("Warning: Could not load users from file. Creating default admin user.");
        }
    }
}
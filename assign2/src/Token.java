import java.time.Instant;
import java.util.UUID;

public class Token {
    private String username;
    private String token;
    private Long expirationTime;

    Token(String username, String token, Long expirationTime) {
        this.username = username;
        this.token = token;
        this.expirationTime = expirationTime;
    }

    public static Token generateToken(String username) {
        // Generate a random UUID and convert it to a string
        String tokenString = UUID.randomUUID().toString();
        Long expirationTime = Instant.now().getEpochSecond() + 3600; // Token valid for 1 hour
        Token token = new Token(username, tokenString, expirationTime);
        return token;
    }

    public static Token getToken(String username, String tokenString) {
        // If a token already exists, instantiate it with the given values
        Long expirationTime = Instant.now().getEpochSecond() + 3600; // Token valid for 1 hour
        Token token = new Token(username, tokenString, expirationTime);
        return token;
    }

    public static Token refreshToken(Token existingToken) {
        // Create a new token with refreshed expiration but the same token string
        Long newExpirationTime = Instant.now().getEpochSecond() + 3600; // Reset to 1 hour from now
        return new Token(existingToken.getUsername(), existingToken.getTokenString(), newExpirationTime);
    }

    public boolean isExpired() {
        return Instant.now().getEpochSecond() > expirationTime;
    }
    
    public String getUsername() {
        return username;
    }
    
    public String getTokenString() {
        return token;
    }
    
    public Long getExpirationTime() {
        return expirationTime;
    }

    public String toString() {
        return "Token:" +
                "username='" + username + '\'' +
                ", token='" + token + '\'' +
                ", expirationTime=" + expirationTime;
    }
}
package ExpenseSplitter.controller;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final JdbcTemplate jdbc;

    public AuthController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @PostMapping("/register")
    public Map<String, Object> register(
            @RequestBody Map<String, String> body) {

        String name = body.get("name");
        String email = body.get("email");
        String password = body.get("password");

        if (name == null || name.isBlank()
                || email == null || email.isBlank()
                || password == null || password.isBlank()) {
            throw new IllegalArgumentException(
                    "Name, email and password are required");
        }

        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM users WHERE email = ?",
                Integer.class,
                email);

        if (count != null && count > 0) {
            throw new IllegalArgumentException(
                    "An account with this email already exists");
        }

        jdbc.update(
                "INSERT INTO users (name, email, password) "
                        + "VALUES (?, ?, ?)",
                name, email, password);

        return jdbc.queryForMap(
                "SELECT user_id AS userId, name, email "
                        + "FROM users WHERE email = ?",
                email);
    }

    @PostMapping("/login")
    public Map<String, Object> login(
            @RequestBody LoginRequest request) {

        String email = request.getEmail();
        String password = request.getPassword();

        if (email == null || email.isBlank()
                || password == null || password.isBlank()) {
            throw new IllegalArgumentException(
                    "Email and password are required");
        }

        return jdbc.query(
                "SELECT user_id, name, email "
                        + "FROM users "
                        + "WHERE email = ? AND password = ?",
                ps -> {
                    ps.setString(1, email);
                    ps.setString(2, password);
                },
                rs -> {

                    if (!rs.next()) {
                        throw new IllegalArgumentException(
                                "Invalid email or password");
                    }

                    Map<String, Object> result =
                            new LinkedHashMap<>();

                    result.put("userId",
                            rs.getInt("user_id"));

                    result.put("name",
                            rs.getString("name"));

                    result.put("email",
                            rs.getString("email"));

                    return result;
                });
    }
}
package ExpenseSplitter.controller;

import ExpenseSplitter.entity.User;
import ExpenseSplitter.service.UserService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public List<User> getAllUsers() {
        return userService.getAllUsers();
    }

    @GetMapping("/{userId}")
    public Map<String, Object> getUserById(
            @PathVariable int userId) {

        return userService.getUserById(userId);
    }
}
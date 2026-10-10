package ExpenseSplitter.service;

import ExpenseSplitter.entity.User;
import ExpenseSplitter.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    public Map<String, Object> getUserById(int userId) {

        User user = userRepository.findById(userId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "User not found"));

        Map<String, Object> result = new LinkedHashMap<>();

        result.put("userId", user.getUserId());
        result.put("name", user.getName());
        result.put("email", user.getEmail());

        return result;
    }
}
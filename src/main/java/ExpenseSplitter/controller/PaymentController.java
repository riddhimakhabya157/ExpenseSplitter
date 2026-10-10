package ExpenseSplitter.controller;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final JdbcTemplate jdbc;

    public PaymentController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @PostMapping
    @Transactional
    public Map<String, Object> recordPayment(
            @RequestBody Map<String, Object> body) {

        if (!(body.get("fromUser") instanceof Number fromValue)
                || !(body.get("toUser") instanceof Number toValue)
                || !(body.get("amount") instanceof Number amountValue)) {

            throw new IllegalArgumentException(
                    "fromUser, toUser and amount are required");
        }

        int fromUser = fromValue.intValue();
        int toUser = toValue.intValue();
        double amount = amountValue.doubleValue();

        if (fromUser == toUser || amount <= 0) {

            throw new IllegalArgumentException(
                    "Payment must be positive and users must be different");
        }

        Integer fromExists = jdbc.queryForObject(
                "SELECT COUNT(*) FROM users WHERE user_id = ?",
                Integer.class,
                fromUser);

        Integer toExists = jdbc.queryForObject(
                "SELECT COUNT(*) FROM users WHERE user_id = ?",
                Integer.class,
                toUser);

        if (fromExists == null || fromExists == 0
                || toExists == null || toExists == 0) {

            throw new IllegalArgumentException(
                    "Both users must exist");
        }

        String dateText = (String) body.get("paymentDate");

        Date paymentDate = Date.valueOf(
                dateText == null || dateText.isBlank()
                        ? LocalDate.now()
                        : LocalDate.parse(dateText));

        jdbc.update(
                "INSERT INTO payments "
                        + "(from_user, to_user, amount, payment_date) "
                        + "VALUES (?, ?, ?, ?)",
                fromUser,
                toUser,
                amount,
                paymentDate);

        return Map.of(
                "message",
                "Payment recorded successfully");
    }

    @GetMapping("/{userId}")
    public List<Map<String, Object>> getPayments(
            @PathVariable int userId) {

        return jdbc.queryForList(
                "SELECT "
                        + "p.payment_id AS paymentId, "
                        + "p.from_user AS fromUser, "
                        + "f.name AS fromName, "
                        + "p.to_user AS toUser, "
                        + "t.name AS toName, "
                        + "p.amount, "
                        + "DATE_FORMAT(p.payment_date, '%Y-%m-%d') "
                        + "AS paymentDate "
                        + "FROM payments p "
                        + "JOIN users f "
                        + "ON f.user_id = p.from_user "
                        + "JOIN users t "
                        + "ON t.user_id = p.to_user "
                        + "WHERE p.from_user = ? "
                        + "OR p.to_user = ? "
                        + "ORDER BY p.payment_date DESC, "
                        + "p.payment_id DESC",
                userId,
                userId);
    }
}
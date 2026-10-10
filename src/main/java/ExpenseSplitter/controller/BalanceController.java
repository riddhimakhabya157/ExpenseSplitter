package ExpenseSplitter.controller;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/balance")
public class BalanceController {

    private final JdbcTemplate jdbc;

    public BalanceController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping("/{userId}")
    public Map<String, Object> getBalance(@PathVariable int userId) {

        Double groupPaid = jdbc.queryForObject(
                "SELECT COALESCE(SUM(amount), 0) FROM expenses "
                        + "WHERE paid_by = ? AND expense_type = 'group'",
                Double.class, userId);

        Double groupShares = jdbc.queryForObject(
                "SELECT COALESCE(SUM(share_amount), 0) "
                        + "FROM expense_members WHERE user_id = ? "
                        + "AND expense_id IN "
                        + "(SELECT expense_id FROM expenses "
                        + "WHERE expense_type = 'group')",
                Double.class, userId);

        Double personalPaid = jdbc.queryForObject(
                "SELECT COALESCE(SUM(amount), 0) FROM expenses "
                        + "WHERE paid_by = ? AND expense_type = 'personal'",
                Double.class, userId);

        Double personalOwed = jdbc.queryForObject(
                "SELECT COALESCE(SUM(amount), 0) "
                        + "FROM personal_expenses WHERE owed_by = ?",
                Double.class, userId);

        Double paymentsMade = jdbc.queryForObject(
                "SELECT COALESCE(SUM(amount), 0) "
                        + "FROM payments WHERE from_user = ?",
                Double.class, userId);

        Double paymentsReceived = jdbc.queryForObject(
                "SELECT COALESCE(SUM(amount), 0) "
                        + "FROM payments WHERE to_user = ?",
                Double.class, userId);

        double net = value(groupPaid) - value(groupShares)
                + value(personalPaid) - value(personalOwed)
                + value(paymentsMade) - value(paymentsReceived);

        net = Math.round(net * 100.0) / 100.0;

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("userId", userId);
        result.put("youOwe", Math.max(0, -net));
        result.put("youAreOwed", Math.max(0, net));
        result.put("netBalance", net);
        result.put("message", net > 0
                ? "Others owe you money."
                : net < 0
                ? "You owe money."
                : "You are all settled up.");

        return result;
    }

    private double value(Double number) {
        return number == null ? 0.0 : number;
    }
}
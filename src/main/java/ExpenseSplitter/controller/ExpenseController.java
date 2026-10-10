package ExpenseSplitter.controller;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/expenses")
public class ExpenseController {

    private final JdbcTemplate jdbc;

    public ExpenseController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @PostMapping
    @Transactional
    public Map<String, Object> addExpense(
            @RequestBody Map<String, Object> body) {

        String description = (String) body.get("description");
        Number amountValue = (Number) body.get("amount");
        Number paidByValue = (Number) body.get("paidBy");

        if (description == null || description.isBlank()
                || amountValue == null || paidByValue == null) {
            throw new IllegalArgumentException(
                    "Description, amount and paidBy are required");
        }

        double amount = amountValue.doubleValue();
        int paidBy = paidByValue.intValue();

        String type = String.valueOf(
                body.getOrDefault("expenseType", "group"));

        if (amount <= 0
                || (!type.equals("group")
                && !type.equals("personal"))) {
            throw new IllegalArgumentException(
                    "Amount must be positive and type must be group or personal");
        }

        String dateText = (String) body.get("expenseDate");

        Date expenseDate = Date.valueOf(
                dateText == null || dateText.isBlank()
                        ? LocalDate.now()
                        : LocalDate.parse(dateText));

        KeyHolder keyHolder = new GeneratedKeyHolder();

        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO expenses "
                            + "(description, amount, paid_by, "
                            + "expense_type, expense_date) "
                            + "VALUES (?, ?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS);

            ps.setString(1, description);
            ps.setDouble(2, amount);
            ps.setInt(3, paidBy);
            ps.setString(4, type);
            ps.setDate(5, expenseDate);

            return ps;
        }, keyHolder);

        Number generatedId = keyHolder.getKey();

        if (generatedId == null) {
            throw new IllegalStateException(
                    "Could not create expense");
        }

        int expenseId = generatedId.intValue();

        if (type.equals("group")) {

            Object membersObject = body.get("members");

            if (!(membersObject instanceof List<?> members)
                    || members.isEmpty()) {
                throw new IllegalArgumentException(
                        "Group expenses require a non-empty members list");
            }

            double totalShares = 0;

            for (Object item : members) {

                if (!(item instanceof Map<?, ?> member)
                        || !(member.get("userId") instanceof Number userId)
                        || !(member.get("shareAmount") instanceof Number share)) {
                    throw new IllegalArgumentException(
                            "Each member needs userId and shareAmount");
                }

                double shareAmount = share.doubleValue();

                if (shareAmount < 0) {
                    throw new IllegalArgumentException(
                            "Share amounts cannot be negative");
                }

                totalShares += shareAmount;

                jdbc.update(
                        "INSERT INTO expense_members "
                                + "(expense_id, user_id, share_amount) "
                                + "VALUES (?, ?, ?)",
                        expenseId,
                        userId.intValue(),
                        shareAmount);
            }

            if (Math.abs(totalShares - amount) > 0.01) {
                throw new IllegalArgumentException(
                        "Group member shares must add up to the total expense amount");
            }

        } else {

            Object owedByObject = body.get("owedBy");

            if (!(owedByObject instanceof Number owedByValue)) {
                throw new IllegalArgumentException(
                        "Personal expenses require owedBy");
            }

            jdbc.update(
                    "INSERT INTO personal_expenses "
                            + "(expense_id, owed_by, amount) "
                            + "VALUES (?, ?, ?)",
                    expenseId,
                    owedByValue.intValue(),
                    amount);
        }

        return Map.of(
                "message", "Expense added successfully",
                "expenseId", expenseId);
    }

    @GetMapping("/history/{userId}")
    public List<Map<String, Object>> getExpenseHistory(
            @PathVariable int userId) {

        return jdbc.queryForList(
                "SELECT "
                        + "e.expense_id AS expenseId, "
                        + "e.description, "
                        + "e.amount, "
                        + "e.expense_type AS expenseType, "
                        + "DATE_FORMAT(e.expense_date, '%Y-%m-%d') "
                        + "AS expenseDate, "
                        + "e.paid_by AS paidBy, "
                        + "u.name AS paidByName, "
                        + "CASE "
                        + "WHEN e.expense_type = 'group' THEN "
                        + "COALESCE(("
                        + "SELECT SUM(em.share_amount) "
                        + "FROM expense_members em "
                        + "WHERE em.expense_id = e.expense_id "
                        + "AND em.user_id = ?"
                        + "), 0) "
                        + "WHEN e.expense_type = 'personal' THEN "
                        + "COALESCE(("
                        + "SELECT SUM(pe.amount) "
                        + "FROM personal_expenses pe "
                        + "WHERE pe.expense_id = e.expense_id "
                        + "AND pe.owed_by = ?"
                        + "), 0) "
                        + "ELSE 0 "
                        + "END AS yourShare "
                        + "FROM expenses e "
                        + "JOIN users u ON u.user_id = e.paid_by "
                        + "WHERE e.paid_by = ? "
                        + "OR EXISTS ("
                        + "SELECT 1 FROM expense_members em "
                        + "WHERE em.expense_id = e.expense_id "
                        + "AND em.user_id = ?) "
                        + "OR EXISTS ("
                        + "SELECT 1 FROM personal_expenses pe "
                        + "WHERE pe.expense_id = e.expense_id "
                        + "AND pe.owed_by = ?) "
                        + "ORDER BY e.expense_date DESC, "
                        + "e.expense_id DESC",
                userId,
                userId,
                userId,
                userId,
                userId);
    }
}
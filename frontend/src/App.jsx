
import { useCallback, useEffect, useState } from "react";
import "./App.css";

const API = "http://localhost:8081/api";
const USER_ID = 1;

function money(value) {
  return new Intl.NumberFormat("en-IN", {
    style: "currency",
    currency: "INR",
    minimumFractionDigits: 2,
  }).format(Number(value) || 0);
}

async function apiRequest(path, options = {}) {
  const response = await fetch(`${API}${path}`, {
    ...options,
    headers: {
      "Content-Type": "application/json",
      ...options.headers,
    },
  });

  const text = await response.text();
  let data;

  try {
    data = text ? JSON.parse(text) : {};
  } catch {
    data = text;
  }

  if (!response.ok) {
    throw new Error(
      data?.error || data?.message || `Request failed (${response.status})`
    );
  }

  return data;
}

function App() {
  const [page, setPage] = useState("Dashboard");
  const [users, setUsers] = useState([]);
  const [expenses, setExpenses] = useState([]);
  const [balance, setBalance] = useState(null);
  const [payments, setPayments] = useState([]);
  const [showForm, setShowForm] = useState(false);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");
  const [success, setSuccess] = useState("");

  const [title, setTitle] = useState("");
  const [amount, setAmount] = useState("");
  const [type, setType] = useState("group");
  const [paidBy, setPaidBy] = useState(String(USER_ID));
  const [owedBy, setOwedBy] = useState(String(USER_ID));
  const [selectedMembers, setSelectedMembers] = useState(
    [String(USER_ID)]
  );

  const loadData = useCallback(async () => {
    setLoading(true);
    setError("");

    const results = await Promise.allSettled([
      apiRequest("/users"),
      apiRequest(`/expenses/history/${USER_ID}`),
      apiRequest(`/balance/${USER_ID}`),
      apiRequest(`/payments/${USER_ID}`),
    ]);

    if (results[0].status === "fulfilled") {
      setUsers(results[0].value);
    }

    if (results[1].status === "fulfilled") {
      setExpenses(results[1].value);
    }

    if (results[2].status === "fulfilled") {
      setBalance(results[2].value);
    }

    if (results[3].status === "fulfilled") {
      setPayments(results[3].value);
    }

    const failed = results.find((result) => result.status === "rejected");
    if (failed) {
      setError(failed.reason?.message || "Some backend data could not be loaded.");
    }

    setLoading(false);
  }, []);

  useEffect(() => {
    loadData();
  }, [loadData]);

  const totalExpenses = expenses.reduce(
    (sum, expense) => sum + Number(expense.amount || 0),
    0
  );

  function toggleMember(userId) {
    setSelectedMembers((current) =>
      current.includes(String(userId))
        ? current.filter((id) => id !== String(userId))
        : [...current, String(userId)]
    );
  }

  async function addExpense(event) {
    event.preventDefault();
    setError("");
    setSuccess("");

    const numericAmount = Number(amount);

    if (!title.trim() || !Number.isFinite(numericAmount) || numericAmount <= 0) {
      setError("Enter an expense name and a positive amount.");
      return;
    }

    if (type === "group" && selectedMembers.length === 0) {
      setError("Select at least one member for the group expense.");
      return;
    }

    if (!users.some((user) => String(user.userId) === paidBy)) {
      setError("Select a valid person who paid.");
      return;
    }

    const payload = {
      description: title.trim(),
      amount: numericAmount,
      paidBy: Number(paidBy),
      expenseType: type,
      expenseDate: new Date().toLocaleDateString("en-CA"),
    };

    if (type === "group") {
      const memberIds = [...new Set(selectedMembers.map(Number))];
      const share = Math.floor((numericAmount / memberIds.length) * 100) / 100;
      let assigned = 0;

      payload.members = memberIds.map((userId, index) => {
        const shareAmount =
          index === memberIds.length - 1
            ? Math.round((numericAmount - assigned) * 100) / 100
            : share;

        assigned += shareAmount;
        return { userId, shareAmount };
      });
    } else {
      payload.owedBy = Number(owedBy);
    }

    setSaving(true);

    try {
      await apiRequest("/expenses", {
        method: "POST",
        body: JSON.stringify(payload),
      });

      setSuccess("Expense saved successfully.");
      setTitle("");
      setAmount("");
      setShowForm(false);
      await loadData();
      setPage("History");
    } catch (err) {
      setError(err.message || "Could not save the expense.");
    } finally {
      setSaving(false);
    }
  }

  const menu = ["Dashboard", "Expenses", "History", "Balances", "Payments"];
  const currentUser = users.find(
    (user) => Number(user.userId) === USER_ID
  );
  const displayName = currentUser?.name || "Riddhima";

  function userName(id) {
    return users.find((user) => Number(user.userId) === Number(id))?.name
      || `User ${id}`;
  }

  return (
    <div className="app-shell">
      <aside className="sidebar">
        <a
          className="brand"
          href="#dashboard"
          onClick={(event) => {
            event.preventDefault();
            setPage("Dashboard");
          }}
        >
          <span className="brand-icon">S</span>
          Split<span className="brand-light">wise</span>
        </a>

        <p className="workspace-label">WORKSPACE</p>

        <nav className="side-nav">
          {menu.map((item, index) => (
            <button
              key={item}
              className={`nav-item ${page === item ? "active" : ""}`}
              onClick={() => {
                setPage(item);
                setError("");
                setSuccess("");
              }}
            >
              <span className="nav-icon">
                {["▦", "↗", "◷", "⇄", "▤"][index]}
              </span>
              {item}
            </button>
          ))}
        </nav>

        <div className="sidebar-bottom">
          <div className="help-card">
            <strong>Need a hand?</strong>
            <p>Keep your group expenses simple and stress-free.</p>
          </div>
          <div className="profile">
            <div className="avatar">{displayName.charAt(0).toUpperCase()}</div>
            <div className="profile-info">
              <strong>{displayName}</strong>
              <span>Personal account</span>
            </div>
          </div>
        </div>
      </aside>

      <main className="main-content">
        <header className="topbar">
          <span className="breadcrumb">Workspace / {page}</span>
          <span className="date-label">
            {new Date().toLocaleDateString("en-IN", {
              month: "long",
              year: "numeric",
            })}
          </span>
        </header>

        <div className="page-content">
          <section className="welcome-row">
            <div>
              <div className="eyebrow">YOUR MONEY, MADE SIMPLE</div>
              <h1>
                {page === "Dashboard"
                  ? `Good afternoon, ${displayName}`
                  : page}
              </h1>
              <p className="page-subtitle">
                Here's what's happening with your expenses.
              </p>
            </div>

            <button
              className="primary-button"
              onClick={() => {
                setError("");
                setSuccess("");
                setShowForm(true);
              }}
            >
              + Add expense
            </button>
          </section>

          {error && (
            <div role="alert" style={{ color: "#b42318", marginBottom: 16 }}>
              {error}
            </div>
          )}

          {success && (
            <div role="status" style={{ color: "#218653", marginBottom: 16 }}>
              {success}
            </div>
          )}

          {loading && (
            <p role="status">Loading your data from the backend...</p>
          )}

          {page === "Dashboard" && (
            <>
              <section className="summary-grid">
                <article className="summary-card total-card">
                  <div className="card-heading">Total expenses in your history</div>
                  <h2>{money(totalExpenses)}</h2>
                  <div className="card-footer">
                    {expenses.length} recorded expenses
                  </div>
                </article>

                <article className="summary-card">
                  <div className="card-heading">You owe</div>
                  <h2>{money(balance?.youOwe)}</h2>
                  <div className="card-footer">
                    {balance?.message || "Balance from your account"}
                  </div>
                </article>

                <article className="summary-card">
                  <div className="card-heading">You are owed</div>
                  <h2>{money(balance?.youAreOwed)}</h2>
                  <div className="card-footer">Based on recorded expenses and payments</div>
                </article>
              </section>

              <section className="insight-banner">
                <div>
                  <strong>Sharing is caring. Tracking is smarter.</strong>
                  <p>Keep every shared expense organized in one place.</p>
                </div>
                <button className="text-button" onClick={() => setPage("Balances")}>
                  View balances →
                </button>
              </section>
            </>
          )}

          {page === "Balances" && (
            <section className="empty-panel">
              <h2>Your balances</h2>
              {balance ? (
                <>
                  <p>You owe: <strong>{money(balance.youOwe)}</strong></p>
                  <p>You are owed: <strong>{money(balance.youAreOwed)}</strong></p>
                  <p>{balance.message}</p>
                </>
              ) : (
                <p>Balance information is not available yet.</p>
              )}
            </section>
          )}

          {page === "Payments" && (
            <section className="expenses-panel">
              <div className="section-heading">
                <div>
                  <h2>Payment history</h2>
                  <p>Payments involving your account.</p>
                </div>
              </div>
              <div className="table-wrap">
                <table>
                  <thead>
                    <tr>
                      <th>FROM</th>
                      <th>TO</th>
                      <th>DATE</th>
                      <th className="amount-cell">AMOUNT</th>
                    </tr>
                  </thead>
                  <tbody>
                    {payments.map((payment) => (
                      <tr key={payment.paymentId}>
                        <td>{payment.fromName}</td>
                        <td>{payment.toName}</td>
                        <td>{payment.paymentDate}</td>
                        <td className="amount-cell amount-value">
                          {money(payment.amount)}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
              {!payments.length && !loading && (
                <p style={{ padding: 20 }}>No payments found for this account.</p>
              )}
            </section>
          )}

          {(page === "Expenses" || page === "History" || page === "Dashboard") && (
            <section className="expenses-panel">
              <div className="section-heading">
                <div>
                  <h2>
                    {page === "Expenses"
                      ? "Group expenses"
                      : page === "History"
                        ? "Expense history"
                        : "Recent expenses"}
                  </h2>
                  <p>Live records loaded from your backend.</p>
                </div>
                <button
                  className="text-button"
                  onClick={() => setPage(page === "History" ? "Dashboard" : "History")}
                >
                  {page === "History" ? "Back to dashboard" : "View all →"}
                </button>
              </div>

              <div className="table-wrap">
                <table>
                  <thead>
                    <tr>
                      <th>EXPENSE</th>
                      <th>DATE</th>
                      <th>PAID BY</th>
                      <th>TYPE</th>
                      <th className="amount-cell">AMOUNT</th>
                    </tr>
                  </thead>
                  <tbody>
                    {expenses
                      .filter((expense) =>
                        page !== "Expenses" || expense.expenseType === "group"
                      )
                      .slice(0, page === "Dashboard" ? 5 : undefined)
                      .map((expense) => (
                        <tr key={expense.expenseId}>
                          <td>
                            <div className="expense-name">
                              <span className="expense-icon">₹</span>
                              <div>
                                <strong>{expense.description}</strong>
                                <span>
                                  Your share: {money(expense.yourShare)}
                                </span>
                              </div>
                            </div>
                          </td>
                          <td className="muted-cell">{expense.expenseDate}</td>
                          <td>{expense.paidByName || userName(expense.paidBy)}</td>
                          <td>
                            <span className={`type-badge ${expense.expenseType}`}>
                              {expense.expenseType}
                            </span>
                          </td>
                          <td className="amount-cell amount-value">
                            {money(expense.amount)}
                          </td>
                        </tr>
                      ))}
                  </tbody>
                </table>
              </div>

              {!expenses.length && !loading && (
                <p style={{ padding: 20 }}>No expenses found for this account.</p>
              )}

              <div className="table-bottom">
                <span>{expenses.length} recorded expenses</span>
                <span>Currency: INR (₹)</span>
              </div>
            </section>
          )}

          <footer className="page-footer">
            <span>ExpenseSplitter · 2026</span>
            <span>Made for simpler sharing ♥</span>
          </footer>
        </div>
      </main>

      {showForm && (
        <div className="modal-overlay" onClick={() => setShowForm(false)}>
          <section
            className="expense-modal"
            onClick={(event) => event.stopPropagation()}
          >
            <div className="modal-heading">
              <div>
                <h2>Add an expense</h2>
                <p>This expense will be saved to the backend.</p>
              </div>
              <button
                className="close-button"
                onClick={() => setShowForm(false)}
                type="button"
                aria-label="Close form"
              >
                ×
              </button>
            </div>

            <form onSubmit={addExpense}>
              <label htmlFor="expense-title">Expense name</label>
              <input
                id="expense-title"
                value={title}
                onChange={(event) => setTitle(event.target.value)}
                placeholder="e.g. Dinner with friends"
                required
              />

              <label htmlFor="expense-amount">Amount (₹)</label>
              <input
                id="expense-amount"
                type="number"
                min="0.01"
                step="0.01"
                value={amount}
                onChange={(event) => setAmount(event.target.value)}
                placeholder="0.00"
                required
              />

              <label htmlFor="expense-type">Expense type</label>
              <select
                id="expense-type"
                value={type}
                onChange={(event) => setType(event.target.value)}
              >
                <option value="group">Group</option>
                <option value="personal">Personal</option>
              </select>

              <label htmlFor="paid-by">Paid by</label>
              <select
                id="paid-by"
                value={paidBy}
                onChange={(event) => setPaidBy(event.target.value)}
                required
              >
                {users.map((user) => (
                  <option key={user.userId} value={String(user.userId)}>
                    {user.name}
                  </option>
                ))}
              </select>

              {type === "group" ? (
                <>
                  <label>Split equally among</label>
                  <div style={{ display: "grid", gap: 8, margin: "5px 0 10px" }}>
                    {users.map((user) => (
                      <label
                        key={user.userId}
                        style={{
                          display: "flex",
                          alignItems: "center",
                          gap: 8,
                          fontWeight: 400,
                        }}
                      >
                        <input
                          type="checkbox"
                          checked={selectedMembers.includes(String(user.userId))}
                          onChange={() => toggleMember(user.userId)}
                        />
                        {user.name}
                      </label>
                    ))}
                  </div>
                </>
              ) : (
                <>
                  <label htmlFor="owed-by">Expense belongs to</label>
                  <select
                    id="owed-by"
                    value={owedBy}
                    onChange={(event) => setOwedBy(event.target.value)}
                  >
                    {users.map((user) => (
                      <option key={user.userId} value={String(user.userId)}>
                        {user.name}
                      </option>
                    ))}
                  </select>
                </>
              )}

              <p className="form-note">
                Group expenses are split equally between selected members.
              </p>

              <button
                className="primary-button submit-button"
                type="submit"
                disabled={saving || loading}
              >
                {saving ? "Saving..." : "Save expense →"}
              </button>
            </form>
          </section>
        </div>
      )}
    </div>
  );
}

export default App;

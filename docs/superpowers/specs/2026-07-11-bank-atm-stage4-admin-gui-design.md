# Stage 4 — Admin GUI (JavaFX, MVP)

**Project:** Bank Management System & ATM Simulator
**Stage:** 4 of 4 (Bank Admin GUI)
**Date:** 2026-07-11
**Status:** Approved design

---

## 1. Context & Scope

Stages 1–3 delivered the DB foundation, the service layer, and the Customer ATM
JavaFX GUI (MVP). Stage 4 adds the **Bank Admin GUI** as a second flow in the
same JavaFX app, reusing the Stage 3 MVP pattern (thin views, tested presenters,
a navigator interface). It is the final stage.

Series recap: **1. DB (done) → 2. Service layer (done) → 3. Customer GUI (done)
→ 4. Admin GUI (this spec).**

### In scope
- A **Role-Select** landing screen: Customer (existing flow) or Admin.
- Admin flow: Admin Login, Admin Menu, All Accounts (list), Admin Open Account,
  Manage Account (view details + transaction history, Block/Close).
- Small service additions: `AccountService.listAllAccounts()`,
  `AccountService.accountHistory(long)`, and a new `AdminService`
  (`createAdmin`, `ensureDefaultAdmin`).
- **Admin bootstrap:** auto-seed a default admin `admin` / `admin123` (BCrypt-
  hashed) on first run if the `admins` table is empty.
- Headless presenter/service tests against `bank_test`.

### Out of scope (deferred / YAGNI)
- Editing admins beyond seeding + create; admin roles/permissions.
- Editing customer details (name/PIN) by admin; deposits/withdrawals by admin.
- Deleting accounts (only Block/Close).
- Automated JavaFX-widget UI tests (views verified by running the app).
- The Stage 3 cosmetic backlog items (error-label clearing, etc.).

### Environment
Java 17, Maven, JavaFX 21, MySQL 9.7 (`bank`/`bank_test`), JUnit 5. No new deps.

---

## 2. Architecture — reuse Stage 3 MVP

The admin flow mirrors the customer flow: thin `*ViewFx` views behind view
interfaces, plain-Java presenters holding all logic (no JavaFX import), tested
headless against the real services. `App` wires everything.

```
com.bank.ui
├── App.java              (MODIFIED: implements Navigator AND AdminNavigator; starts at role-select)
├── Navigator.java        (unchanged — customer routes)
├── AdminNavigator.java   (NEW — admin routes)
├── AdminSession.java     (NEW — logged-in admin username + selected account number)
├── Session.java, Messages.java (unchanged)
├── view/
│   ├── RoleSelectView(+Fx)      (NEW)
│   ├── AdminLoginView(+Fx)      (NEW)
│   ├── AdminMenuView(+Fx)       (NEW)
│   ├── AllAccountsView(+Fx)     (NEW)
│   ├── AdminOpenAccountView(+Fx)(NEW)
│   └── ManageAccountView(+Fx)   (NEW)
└── presenter/
    ├── AdminLoginPresenter      (NEW)
    ├── AdminMenuPresenter       (NEW)
    ├── AllAccountsPresenter     (NEW)
    ├── AdminOpenAccountPresenter(NEW)
    └── ManageAccountPresenter   (NEW)
```

### `AdminNavigator` (interface)
`showRoleSelect()`, `showAdminLogin()`, `showAdminMenu()`, `showAllAccounts()`,
`showAdminOpenAccount()`, `showManageAccount(long accountNumber)`.

`App` implements both `Navigator` and `AdminNavigator`. The Role-Select view's
two buttons are wired by `App` directly to `showLogin()` (customer) and
`showAdminLogin()` (admin) — no logic, no presenter.

### `AdminSession`
Holds `String username` (set on admin login, cleared on logout) and
`Long selectedAccount` (set when an account is chosen from All Accounts, read by
Manage Account). Methods: `setAdmin(String)`, `clear()`, `isLoggedIn()`,
`String requireAdmin()`, `selectAccount(long)`, `long requireSelectedAccount()`.

---

## 3. Screens

| Screen | Inputs | Presenter action | Success | Error |
|--------|--------|------------------|---------|-------|
| **Role Select** | — | (App-wired) Customer → `showLogin`; Admin → `showAdminLogin` | navigate | — |
| **Admin Login** | username, password | `authService.authenticateAdmin` | set `AdminSession`, `showAdminMenu` | "Invalid username or password." |
| **Admin Menu** | — | routes: All Accounts, Open Account; Logout clears `AdminSession` → `showRoleSelect` | navigate | — |
| **All Accounts** | (row select) | `accountService.listAllAccounts()` → render rows; on select → `adminSession.selectAccount(n)` + `showManageAccount(n)` | list shown | `showError` |
| **Admin Open Account** | name, type, PIN, opening balance | `accountService.openAccount` | show new account number | `Messages.of` (bad PIN/amount) |
| **Manage Account** | — (uses selected account) | on load: show account details + `accountHistory`; Block → `blockAccount`; Close → `closeAccount`; back → `showAllAccounts` | status updated / history shown | `Messages.of` |

Malformed numeric input in Admin Open Account is guarded with "Enter a valid
number." before the service call, exactly like the customer screens.

---

## 4. Service-Layer Additions (TDD)

- **`AccountService.listAllAccounts()`** → `List<Account>`: runs
  `accountDao.findAll(c)` inside a `UnitOfWork`.
- **`AccountService.accountHistory(long acct)`** → `List<Transaction>`: verifies
  the account exists (`loadOrThrow`), returns
  `transactionDao.findByAccountNumber(c, acct)` (all rows, newest-first).
- **`AdminService(UnitOfWork, AdminDao, PasswordHasher)`**:
  - `void createAdmin(String username, String rawPassword)` — stores a new
    `Admin` with the BCrypt-hashed password.
  - `void ensureDefaultAdmin()` — if `adminDao.findByUsername(c, "admin")` is
    empty, create admin `admin` with password `admin123` (hashed). Idempotent —
    safe to call on every startup.

No business rule changes; all additions are thin wrappers over existing DAOs.

---

## 5. Admin Bootstrap

`App.start()` (after `SchemaInitializer.initialize`) calls
`adminService.ensureDefaultAdmin()`. On a fresh database this creates
**`admin` / `admin123`** (password BCrypt-hashed). The Admin Login screen shows
a small hint: "Default admin: admin / admin123 (change in production)". This
resolves the chicken-and-egg problem (an admin must exist to log in, but there
was no way to create the first one).

---

## 6. Error Handling

Reuse the Stage 2 `BankServiceException` hierarchy and `Messages.of` for
account/domain errors (bad PIN, invalid amount, not found). Admin login maps
`AuthenticationException` → **"Invalid username or password."** inside
`AdminLoginPresenter` (so the customer-facing "Invalid account number or PIN."
wording in `Messages` stays intact). Any other `RuntimeException` → the generic
"Something went wrong. Please try again." message, as in Stage 3.

---

## 7. Testing

- **Presenter tests** (headless: fake views + a new `FakeAdminNavigator`,
  against real services on `bank_test`):
  - Admin Login: correct default admin logs in → `AdminSession` set +
    `showAdminMenu`; wrong password → "Invalid username or password.", no nav.
  - Admin Menu: routes to All Accounts / Open Account; logout clears the session
    and returns to role select.
  - All Accounts: after seeding N accounts via the service, the presenter
    renders N rows; selecting one sets the selected account + navigates to
    Manage Account.
  - Admin Open Account: valid input creates an account (verified via the
    service); bad PIN / non-numeric balance → correct error, nothing created.
  - Manage Account: Block sets status BLOCKED, Close sets CLOSED (verified via
    the service); history renders the account's transactions.
- **Service tests:** `listAllAccounts` returns all rows; `accountHistory`
  returns an account's transactions (and throws `AccountNotFoundException` for an
  unknown account); `AdminService.createAdmin` then `authenticateAdmin` succeeds;
  `ensureDefaultAdmin` creates `admin` once and is idempotent on a second call.
- **Fakes** live under `src/test/java/com/bank/ui/`. Tests clean `bank_test`
  FK-safe (`transactions` → `accounts` → `admins`) via `UnitOfWork`.
- `App`, `*ViewFx`, and the Role-Select wiring are verified by `mvn -q compile`
  and by running `mvn javafx:run` manually (never in automation — it blocks).

---

## 8. Success Criteria (Definition of Done for Stage 4)

1. `mvn test` green headless (all prior tests plus new admin presenter/service
   tests) on `bank_test`.
2. `mvn javafx:run` launches to Role Select; **Admin** → log in with
   `admin`/`admin123` → list all accounts, open an account, and block/close an
   account and view its history — each backed by the real services. **Customer**
   still reaches the existing ATM flow unchanged.
3. On a fresh DB, the default admin is seeded exactly once (idempotent).
4. MVP boundary intact: admin presenters import no JavaFX; admin views contain no
   business logic or service calls.
5. Admin password stored only as a BCrypt hash (never plaintext).
6. No customer-flow regressions (the existing 72 tests still pass).

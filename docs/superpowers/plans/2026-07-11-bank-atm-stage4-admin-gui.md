# Stage 4 — Admin GUI Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the Bank Admin GUI as a second JavaFX flow (role-select landing → admin), reusing the Stage 3 MVP pattern, plus the small service additions it needs.

**Architecture:** MVP. Thin admin `*ViewFx` views behind view interfaces; plain-Java admin presenters (no JavaFX import) holding all logic, tested headless against the real services on `bank_test` with fake views + a `FakeAdminNavigator`. `App` implements both `Navigator` (customer) and `AdminNavigator` (admin) and starts at a role-select screen. A default admin is auto-seeded on first run.

**Tech Stack:** Java 17, Maven, JavaFX 21, JUnit 5, MySQL (`bank_test`), the existing services. No new dependencies.

## Global Constraints

- Java 17; JavaFX 21. No new dependencies.
- MVP boundary: **admin view classes contain no logic and no service calls**; **admin presenter classes import no JavaFX** and depend on the `AdminNavigator` interface, never on `App`.
- Reuse the Stage 2 `BankServiceException` hierarchy and `Messages.of` for account/domain errors. **Admin login** maps `AuthenticationException` → `"Invalid username or password."` inside `AdminLoginPresenter` (do not change `Messages`). Other `RuntimeException` → `"Something went wrong. Please try again."`.
- Malformed numeric input in Admin Open Account → `"Enter a valid number."` BEFORE any service call.
- Admin passwords stored ONLY as BCrypt hashes (via `PasswordHasher`); default admin is `admin` / `admin123`, seeded once by `ensureDefaultAdmin()` (idempotent) from `App.start()` after schema init.
- Money is `BigDecimal`.
- Presenter/service tests are headless against real MySQL `bank_test`; clean tables FK-safe (`transactions` → `accounts` → `admins`) via `UnitOfWork`.
- **Do NOT run `mvn javafx:run` in automation** — it blocks. Verify `App`/`*ViewFx`/role-select via `mvn -q compile`; verify presenters/services/`AdminSession` via `mvn test`.
- Package root `com.bank`; new UI code under `com.bank.ui`, `com.bank.ui.view`, `com.bank.ui.presenter`. The existing 72 tests must still pass.

## Prerequisite (already satisfied)

`bank`/`bank_test` exist; `src/test/resources/db.test.properties` has working credentials. No schema change in Stage 4.

---

## File Structure

- Modify: `com/bank/service/AccountService.java` — add `listAllAccounts`, `accountHistory`, `getAccount`.
- Create: `com/bank/service/AdminService.java`.
- Create: `com/bank/ui/AdminNavigator.java`, `AdminSession.java`.
- Create: `com/bank/ui/view/` — `RoleSelectViewFx`, `AdminLoginView(+Fx)`, `AdminMenuView(+Fx)`, `AllAccountsView(+Fx)`, `AccountRow`, `AdminOpenAccountView(+Fx)`, `ManageAccountView(+Fx)`.
- Create: `com/bank/ui/presenter/` — `AdminLoginPresenter`, `AdminMenuPresenter`, `AllAccountsPresenter`, `AdminOpenAccountPresenter`, `ManageAccountPresenter`.
- Modify: `com/bank/ui/App.java` — implement `AdminNavigator`, build `AdminService`+`AdminSession`, call `ensureDefaultAdmin()`, start at role-select.
- Create tests under `src/test/java/com/bank/...` + `FakeAdminNavigator`.
- Modify: `README.md`.

---

## Task 1: AccountService admin-read methods

**Files:**
- Modify: `src/main/java/com/bank/service/AccountService.java`
- Test: `src/test/java/com/bank/service/AccountServiceAdminReadsTest.java`

**Interfaces:**
- Consumes: `UnitOfWork`, `AccountDao`, `TransactionDao`, `loadOrThrow` (existing private helper).
- Produces (added to `AccountService`):
  - `List<Account> listAllAccounts()`
  - `Account getAccount(long acct)` (throws `AccountNotFoundException` if absent)
  - `List<Transaction> accountHistory(long acct)` (throws `AccountNotFoundException` if absent)

- [ ] **Step 1: Write the failing test**

`src/test/java/com/bank/service/AccountServiceAdminReadsTest.java`:

```java
package com.bank.service;

import com.bank.dao.AccountDaoJdbc;
import com.bank.dao.DaoException;
import com.bank.dao.TransactionDaoJdbc;
import com.bank.db.Database;
import com.bank.db.SchemaInitializer;
import com.bank.db.UnitOfWork;
import com.bank.model.Account;
import com.bank.model.AccountType;
import com.bank.security.PasswordHasher;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

class AccountServiceAdminReadsTest {

    private static final Database db = Database.fromResource("db.test.properties");
    private static final UnitOfWork uow = new UnitOfWork(db);
    private final AccountService service =
            new AccountService(uow, new AccountDaoJdbc(), new TransactionDaoJdbc(), new PasswordHasher());

    @BeforeAll static void schema() { SchemaInitializer.initialize(db); }

    @BeforeEach
    void clean() {
        uow.executeVoid(c -> {
            try (Statement st = c.createStatement()) {
                st.execute("DELETE FROM transactions");
                st.execute("DELETE FROM accounts");
            } catch (SQLException e) {
                throw new DaoException("clean failed", e);
            }
        });
    }

    @Test
    void listAllAccountsReturnsEveryAccount() {
        service.openAccount("Asha", "1234", AccountType.SAVINGS, new BigDecimal("100.00"));
        service.openAccount("Ben", "5678", AccountType.CURRENT, new BigDecimal("0.00"));
        assertEquals(2, service.listAllAccounts().size());
    }

    @Test
    void getAccountReturnsItOrThrows() {
        Account a = service.openAccount("Asha", "1234", AccountType.SAVINGS, new BigDecimal("10.00"));
        assertEquals("Asha", service.getAccount(a.getAccountNumber()).getHolderName());
        assertThrows(AccountNotFoundException.class, () -> service.getAccount(1L));
    }

    @Test
    void accountHistoryReturnsTransactionsOrThrows() {
        Account a = service.openAccount("Asha", "1234", AccountType.SAVINGS, new BigDecimal("100.00"));
        service.deposit(a.getAccountNumber(), new BigDecimal("20.00"));
        service.withdraw(a.getAccountNumber(), new BigDecimal("5.00"));
        assertEquals(3, service.accountHistory(a.getAccountNumber()).size());
        assertThrows(AccountNotFoundException.class, () -> service.accountHistory(1L));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q -Dtest=AccountServiceAdminReadsTest test`
Expected: FAIL — cannot find symbol `listAllAccounts` / `getAccount` / `accountHistory`.

- [ ] **Step 3: Add the three methods to `AccountService`**

Insert into `src/main/java/com/bank/service/AccountService.java` after the `miniStatement` method (before `changePin`):

```java
    public List<Account> listAllAccounts() {
        return uow.execute(accountDao::findAll);
    }

    public Account getAccount(long acct) {
        return uow.execute(c -> loadOrThrow(c, acct));
    }

    public List<Transaction> accountHistory(long acct) {
        return uow.execute(c -> {
            loadOrThrow(c, acct);
            return transactionDao.findByAccountNumber(c, acct);
        });
    }
```

(`Account`, `Transaction`, `List` are already imported in this file.)

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -q -Dtest=AccountServiceAdminReadsTest test`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/bank/service/AccountService.java src/test/java/com/bank/service/AccountServiceAdminReadsTest.java
git commit -m "feat(service): add admin read methods (listAllAccounts, getAccount, accountHistory)"
```

---

## Task 2: AdminService (createAdmin + ensureDefaultAdmin)

**Files:**
- Create: `src/main/java/com/bank/service/AdminService.java`
- Test: `src/test/java/com/bank/service/AdminServiceTest.java`

**Interfaces:**
- Consumes: `UnitOfWork`, `AdminDao`, `PasswordHasher`, `Admin`, `AuthService` (test only).
- Produces: `AdminService(UnitOfWork, AdminDao, PasswordHasher)` with `void createAdmin(String username, String rawPassword)` and `void ensureDefaultAdmin()` (creates `admin`/`admin123` if none; idempotent).

- [ ] **Step 1: Write the failing test**

`src/test/java/com/bank/service/AdminServiceTest.java`:

```java
package com.bank.service;

import com.bank.dao.AccountDaoJdbc;
import com.bank.dao.AdminDaoJdbc;
import com.bank.dao.DaoException;
import com.bank.db.Database;
import com.bank.db.SchemaInitializer;
import com.bank.db.UnitOfWork;
import com.bank.model.Admin;
import com.bank.security.PasswordHasher;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

class AdminServiceTest {

    private static final Database db = Database.fromResource("db.test.properties");
    private static final UnitOfWork uow = new UnitOfWork(db);
    private static final PasswordHasher hasher = new PasswordHasher();
    private final AdminService admins = new AdminService(uow, new AdminDaoJdbc(), hasher);
    private final AuthService auth = new AuthService(uow, new AccountDaoJdbc(), new AdminDaoJdbc(), hasher);

    @BeforeAll static void schema() { SchemaInitializer.initialize(db); }

    @BeforeEach
    void clean() {
        uow.executeVoid(c -> {
            try (Statement st = c.createStatement()) {
                st.execute("DELETE FROM admins");
            } catch (SQLException e) {
                throw new DaoException("clean failed", e);
            }
        });
    }

    private long adminCount() {
        return uow.execute(c -> {
            try (Statement st = c.createStatement();
                 var rs = st.executeQuery("SELECT COUNT(*) FROM admins")) {
                rs.next();
                return rs.getLong(1);
            } catch (SQLException e) { throw new DaoException("count", e); }
        });
    }

    @Test
    void createAdminThenAuthenticate() {
        admins.createAdmin("manager", "secret");
        Admin a = auth.authenticateAdmin("manager", "secret");
        assertEquals("manager", a.getUsername());
        assertNotEquals("secret", a.getPasswordHash());
    }

    @Test
    void ensureDefaultAdminCreatesAdminOnceAndIsIdempotent() {
        admins.ensureDefaultAdmin();
        admins.ensureDefaultAdmin(); // second call must not add another
        assertEquals(1, adminCount());
        assertEquals("admin", auth.authenticateAdmin("admin", "admin123").getUsername());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q -Dtest=AdminServiceTest test`
Expected: FAIL — cannot find symbol `AdminService`.

- [ ] **Step 3: Create `AdminService`**

`src/main/java/com/bank/service/AdminService.java`:

```java
package com.bank.service;

import com.bank.dao.AdminDao;
import com.bank.db.UnitOfWork;
import com.bank.model.Admin;
import com.bank.security.PasswordHasher;

public class AdminService {

    private static final String DEFAULT_USERNAME = "admin";
    private static final String DEFAULT_PASSWORD = "admin123";

    private final UnitOfWork uow;
    private final AdminDao adminDao;
    private final PasswordHasher hasher;

    public AdminService(UnitOfWork uow, AdminDao adminDao, PasswordHasher hasher) {
        this.uow = uow;
        this.adminDao = adminDao;
        this.hasher = hasher;
    }

    public void createAdmin(String username, String rawPassword) {
        uow.executeVoid(c ->
                adminDao.create(c, new Admin(0L, username, hasher.hash(rawPassword))));
    }

    public void ensureDefaultAdmin() {
        uow.executeVoid(c -> {
            if (adminDao.findByUsername(c, DEFAULT_USERNAME).isEmpty()) {
                adminDao.create(c, new Admin(0L, DEFAULT_USERNAME, hasher.hash(DEFAULT_PASSWORD)));
            }
        });
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -q -Dtest=AdminServiceTest test`
Expected: PASS (2 tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/bank/service/AdminService.java src/test/java/com/bank/service/AdminServiceTest.java
git commit -m "feat(service): add AdminService with createAdmin and ensureDefaultAdmin"
```

---

## Task 3: AdminNavigator + AdminSession + FakeAdminNavigator

**Files:**
- Create: `src/main/java/com/bank/ui/AdminNavigator.java`, `AdminSession.java`
- Create (test helper): `src/test/java/com/bank/ui/FakeAdminNavigator.java`
- Test: `src/test/java/com/bank/ui/AdminSessionTest.java`

**Interfaces:**
- Produces:
  - `AdminNavigator`: `showRoleSelect()`, `showAdminLogin()`, `showAdminMenu()`, `showAllAccounts()`, `showAdminOpenAccount()`, `showManageAccount(long accountNumber)`.
  - `AdminSession`: `setAdmin(String)`, `clear()`, `isLoggedIn()`, `String requireAdmin()` (throws if none), `selectAccount(long)`, `long requireSelectedAccount()` (throws if none).
  - `FakeAdminNavigator` (test): booleans `roleSelectShown`, `adminLoginShown`, `adminMenuShown`, `allAccountsShown`, `adminOpenAccountShown`; `Long manageAccountShownFor`.

- [ ] **Step 1: Write the failing test**

`src/test/java/com/bank/ui/FakeAdminNavigator.java`:

```java
package com.bank.ui;

public class FakeAdminNavigator implements AdminNavigator {
    public boolean roleSelectShown, adminLoginShown, adminMenuShown,
            allAccountsShown, adminOpenAccountShown;
    public Long manageAccountShownFor;

    @Override public void showRoleSelect() { roleSelectShown = true; }
    @Override public void showAdminLogin() { adminLoginShown = true; }
    @Override public void showAdminMenu() { adminMenuShown = true; }
    @Override public void showAllAccounts() { allAccountsShown = true; }
    @Override public void showAdminOpenAccount() { adminOpenAccountShown = true; }
    @Override public void showManageAccount(long accountNumber) { manageAccountShownFor = accountNumber; }
}
```

`src/test/java/com/bank/ui/AdminSessionTest.java`:

```java
package com.bank.ui;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AdminSessionTest {
    @Test
    void adminLifecycle() {
        AdminSession s = new AdminSession();
        assertFalse(s.isLoggedIn());
        assertThrows(IllegalStateException.class, s::requireAdmin);
        s.setAdmin("manager");
        assertTrue(s.isLoggedIn());
        assertEquals("manager", s.requireAdmin());
        s.clear();
        assertFalse(s.isLoggedIn());
    }

    @Test
    void selectedAccount() {
        AdminSession s = new AdminSession();
        assertThrows(IllegalStateException.class, s::requireSelectedAccount);
        s.selectAccount(1000000001L);
        assertEquals(1000000001L, s.requireSelectedAccount());
        s.clear();
        assertThrows(IllegalStateException.class, s::requireSelectedAccount);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q -Dtest=AdminSessionTest test`
Expected: FAIL — cannot find symbol `AdminSession` / `AdminNavigator`.

- [ ] **Step 3: Create `AdminNavigator`**

`src/main/java/com/bank/ui/AdminNavigator.java`:

```java
package com.bank.ui;

public interface AdminNavigator {
    void showRoleSelect();
    void showAdminLogin();
    void showAdminMenu();
    void showAllAccounts();
    void showAdminOpenAccount();
    void showManageAccount(long accountNumber);
}
```

- [ ] **Step 4: Create `AdminSession`**

`src/main/java/com/bank/ui/AdminSession.java`:

```java
package com.bank.ui;

public class AdminSession {
    private String username;
    private Long selectedAccount;

    public void setAdmin(String username) {
        this.username = username;
    }

    public void clear() {
        this.username = null;
        this.selectedAccount = null;
    }

    public boolean isLoggedIn() {
        return username != null;
    }

    public String requireAdmin() {
        if (username == null) {
            throw new IllegalStateException("no logged-in admin");
        }
        return username;
    }

    public void selectAccount(long accountNumber) {
        this.selectedAccount = accountNumber;
    }

    public long requireSelectedAccount() {
        if (selectedAccount == null) {
            throw new IllegalStateException("no account selected");
        }
        return selectedAccount;
    }
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `mvn -q -Dtest=AdminSessionTest test`
Expected: PASS (2 tests). Then `mvn -q compile`.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/bank/ui/AdminNavigator.java src/main/java/com/bank/ui/AdminSession.java src/test/java/com/bank/ui/FakeAdminNavigator.java src/test/java/com/bank/ui/AdminSessionTest.java
git commit -m "feat(ui): add AdminNavigator, AdminSession, and FakeAdminNavigator"
```

---

## Task 4: Admin Login screen

**Files:**
- Create: `src/main/java/com/bank/ui/view/AdminLoginView.java`, `AdminLoginViewFx.java`
- Create: `src/main/java/com/bank/ui/presenter/AdminLoginPresenter.java`
- Test: `src/test/java/com/bank/ui/presenter/AdminLoginPresenterTest.java`

**Interfaces:**
- Consumes: `AuthService`, `AdminNavigator`, `AdminSession`, `AdminService` (test, to seed the admin), `FakeAdminNavigator`.
- Produces:
  - `AdminLoginView`: `getUsername()`, `getPassword()`, `showError(String)`, `setOnLogin(Runnable)`.
  - `AdminLoginPresenter(AdminLoginView, AuthService, AdminNavigator, AdminSession)`; `login()`.

- [ ] **Step 1: Write the failing test**

`src/test/java/com/bank/ui/presenter/AdminLoginPresenterTest.java`:

```java
package com.bank.ui.presenter;

import com.bank.dao.AccountDaoJdbc;
import com.bank.dao.AdminDaoJdbc;
import com.bank.dao.DaoException;
import com.bank.db.Database;
import com.bank.db.SchemaInitializer;
import com.bank.db.UnitOfWork;
import com.bank.security.PasswordHasher;
import com.bank.service.AdminService;
import com.bank.service.AuthService;
import com.bank.ui.AdminSession;
import com.bank.ui.FakeAdminNavigator;
import com.bank.ui.view.AdminLoginView;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

class AdminLoginPresenterTest {

    private static final Database db = Database.fromResource("db.test.properties");
    private static final UnitOfWork uow = new UnitOfWork(db);
    private static final PasswordHasher hasher = new PasswordHasher();
    private final AdminService admins = new AdminService(uow, new AdminDaoJdbc(), hasher);
    private final AuthService auth = new AuthService(uow, new AccountDaoJdbc(), new AdminDaoJdbc(), hasher);

    @BeforeAll static void schema() { SchemaInitializer.initialize(db); }

    @BeforeEach
    void clean() {
        uow.executeVoid(c -> {
            try (Statement st = c.createStatement()) {
                st.execute("DELETE FROM admins");
            } catch (SQLException e) {
                throw new DaoException("clean failed", e);
            }
        });
        admins.ensureDefaultAdmin();
    }

    static class FakeAdminLoginView implements AdminLoginView {
        String username = "", password = "", error;
        Runnable onLogin = () -> {};
        @Override public String getUsername() { return username; }
        @Override public String getPassword() { return password; }
        @Override public void showError(String m) { error = m; }
        @Override public void setOnLogin(Runnable h) { onLogin = h; }
    }

    @Test
    void validLoginSetsSessionAndShowsMenu() {
        FakeAdminLoginView view = new FakeAdminLoginView();
        AdminSession session = new AdminSession();
        FakeAdminNavigator nav = new FakeAdminNavigator();
        AdminLoginPresenter presenter = new AdminLoginPresenter(view, auth, nav, session);
        view.username = "admin";
        view.password = "admin123";

        presenter.login();

        assertTrue(nav.adminMenuShown);
        assertTrue(session.isLoggedIn());
        assertEquals("admin", session.requireAdmin());
        assertNull(view.error);
    }

    @Test
    void wrongPasswordShowsError() {
        FakeAdminLoginView view = new FakeAdminLoginView();
        FakeAdminNavigator nav = new FakeAdminNavigator();
        AdminLoginPresenter presenter = new AdminLoginPresenter(view, auth, nav, new AdminSession());
        view.username = "admin";
        view.password = "wrong";

        presenter.login();

        assertFalse(nav.adminMenuShown);
        assertEquals("Invalid username or password.", view.error);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q -Dtest=AdminLoginPresenterTest test`
Expected: FAIL — cannot find symbol `AdminLoginView` / `AdminLoginPresenter`.

- [ ] **Step 3: Create the `AdminLoginView` interface**

`src/main/java/com/bank/ui/view/AdminLoginView.java`:

```java
package com.bank.ui.view;

public interface AdminLoginView {
    String getUsername();
    String getPassword();
    void showError(String message);
    void setOnLogin(Runnable handler);
}
```

- [ ] **Step 4: Create the `AdminLoginPresenter`**

`src/main/java/com/bank/ui/presenter/AdminLoginPresenter.java`:

```java
package com.bank.ui.presenter;

import com.bank.model.Admin;
import com.bank.service.AuthenticationException;
import com.bank.service.AuthService;
import com.bank.ui.AdminNavigator;
import com.bank.ui.AdminSession;
import com.bank.ui.view.AdminLoginView;

public class AdminLoginPresenter {

    private final AdminLoginView view;
    private final AuthService authService;
    private final AdminNavigator navigator;
    private final AdminSession session;

    public AdminLoginPresenter(AdminLoginView view, AuthService authService,
                               AdminNavigator navigator, AdminSession session) {
        this.view = view;
        this.authService = authService;
        this.navigator = navigator;
        this.session = session;
        view.setOnLogin(this::login);
    }

    public void login() {
        try {
            Admin admin = authService.authenticateAdmin(view.getUsername(), view.getPassword());
            session.setAdmin(admin.getUsername());
            navigator.showAdminMenu();
        } catch (AuthenticationException e) {
            view.showError("Invalid username or password.");
        } catch (RuntimeException e) {
            view.showError("Something went wrong. Please try again.");
        }
    }
}
```

- [ ] **Step 5: Create the `AdminLoginViewFx`**

`src/main/java/com/bank/ui/view/AdminLoginViewFx.java`:

```java
package com.bank.ui.view;

import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;

public class AdminLoginViewFx implements AdminLoginView {

    private final TextField usernameField = new TextField();
    private final PasswordField passwordField = new PasswordField();
    private final Button loginButton = new Button("Login");
    private final Label errorLabel = new Label();
    private final VBox root = new VBox(10);

    public AdminLoginViewFx() {
        root.setPadding(new Insets(20));
        usernameField.setPromptText("Username");
        passwordField.setPromptText("Password");
        errorLabel.setStyle("-fx-text-fill: red;");
        Label hint = new Label("Default admin: admin / admin123 (change in production)");
        root.getChildren().addAll(new Label("Admin Login"), usernameField, passwordField,
                loginButton, errorLabel, hint);
    }

    public Parent getRoot() { return root; }

    @Override public String getUsername() { return usernameField.getText(); }
    @Override public String getPassword() { return passwordField.getText(); }
    @Override public void showError(String message) { errorLabel.setText(message); }
    @Override public void setOnLogin(Runnable handler) { loginButton.setOnAction(e -> handler.run()); }
}
```

- [ ] **Step 6: Run test to verify it passes**

Run: `mvn -q -Dtest=AdminLoginPresenterTest test`
Expected: PASS (2 tests). Then `mvn -q compile`.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/bank/ui/view/AdminLoginView.java src/main/java/com/bank/ui/view/AdminLoginViewFx.java src/main/java/com/bank/ui/presenter/AdminLoginPresenter.java src/test/java/com/bank/ui/presenter/AdminLoginPresenterTest.java
git commit -m "feat(ui): add Admin Login screen (view + presenter, tested)"
```

---

## Task 5: Admin Menu screen

**Files:**
- Create: `src/main/java/com/bank/ui/view/AdminMenuView.java`, `AdminMenuViewFx.java`
- Create: `src/main/java/com/bank/ui/presenter/AdminMenuPresenter.java`
- Test: `src/test/java/com/bank/ui/presenter/AdminMenuPresenterTest.java`

**Interfaces:**
- Consumes: `AdminNavigator`, `AdminSession`, `FakeAdminNavigator`.
- Produces:
  - `AdminMenuView`: `setOnAllAccounts(Runnable)`, `setOnOpenAccount(Runnable)`, `setOnLogout(Runnable)`.
  - `AdminMenuPresenter(AdminMenuView, AdminNavigator, AdminSession)`; `logout()` clears the session then `navigator.showRoleSelect()`.

- [ ] **Step 1: Write the failing test**

`src/test/java/com/bank/ui/presenter/AdminMenuPresenterTest.java`:

```java
package com.bank.ui.presenter;

import com.bank.ui.AdminSession;
import com.bank.ui.FakeAdminNavigator;
import com.bank.ui.view.AdminMenuView;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AdminMenuPresenterTest {

    static class FakeAdminMenuView implements AdminMenuView {
        Runnable onAllAccounts, onOpenAccount, onLogout;
        @Override public void setOnAllAccounts(Runnable h) { onAllAccounts = h; }
        @Override public void setOnOpenAccount(Runnable h) { onOpenAccount = h; }
        @Override public void setOnLogout(Runnable h) { onLogout = h; }
    }

    @Test
    void buttonsRouteToNavigator() {
        FakeAdminMenuView view = new FakeAdminMenuView();
        FakeAdminNavigator nav = new FakeAdminNavigator();
        new AdminMenuPresenter(view, nav, new AdminSession());

        view.onAllAccounts.run();
        view.onOpenAccount.run();

        assertTrue(nav.allAccountsShown);
        assertTrue(nav.adminOpenAccountShown);
    }

    @Test
    void logoutClearsSessionAndReturnsToRoleSelect() {
        FakeAdminMenuView view = new FakeAdminMenuView();
        FakeAdminNavigator nav = new FakeAdminNavigator();
        AdminSession session = new AdminSession();
        session.setAdmin("admin");
        new AdminMenuPresenter(view, nav, session);

        view.onLogout.run();

        assertFalse(session.isLoggedIn());
        assertTrue(nav.roleSelectShown);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q -Dtest=AdminMenuPresenterTest test`
Expected: FAIL — cannot find symbol `AdminMenuView` / `AdminMenuPresenter`.

- [ ] **Step 3: Create the `AdminMenuView` interface**

`src/main/java/com/bank/ui/view/AdminMenuView.java`:

```java
package com.bank.ui.view;

public interface AdminMenuView {
    void setOnAllAccounts(Runnable handler);
    void setOnOpenAccount(Runnable handler);
    void setOnLogout(Runnable handler);
}
```

- [ ] **Step 4: Create the `AdminMenuPresenter`**

`src/main/java/com/bank/ui/presenter/AdminMenuPresenter.java`:

```java
package com.bank.ui.presenter;

import com.bank.ui.AdminNavigator;
import com.bank.ui.AdminSession;
import com.bank.ui.view.AdminMenuView;

public class AdminMenuPresenter {

    private final AdminNavigator navigator;
    private final AdminSession session;

    public AdminMenuPresenter(AdminMenuView view, AdminNavigator navigator, AdminSession session) {
        this.navigator = navigator;
        this.session = session;
        view.setOnAllAccounts(navigator::showAllAccounts);
        view.setOnOpenAccount(navigator::showAdminOpenAccount);
        view.setOnLogout(this::logout);
    }

    public void logout() {
        session.clear();
        navigator.showRoleSelect();
    }
}
```

- [ ] **Step 5: Create the `AdminMenuViewFx`**

`src/main/java/com/bank/ui/view/AdminMenuViewFx.java`:

```java
package com.bank.ui.view;

import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

public class AdminMenuViewFx implements AdminMenuView {

    private final Button allAccounts = new Button("All Accounts");
    private final Button openAccount = new Button("Open Account");
    private final Button logout = new Button("Logout");
    private final VBox root = new VBox(10);

    public AdminMenuViewFx() {
        root.setPadding(new Insets(20));
        root.getChildren().addAll(new Label("Admin Menu"), allAccounts, openAccount, logout);
    }

    public Parent getRoot() { return root; }

    @Override public void setOnAllAccounts(Runnable h) { allAccounts.setOnAction(e -> h.run()); }
    @Override public void setOnOpenAccount(Runnable h) { openAccount.setOnAction(e -> h.run()); }
    @Override public void setOnLogout(Runnable h) { logout.setOnAction(e -> h.run()); }
}
```

- [ ] **Step 6: Run test to verify it passes**

Run: `mvn -q -Dtest=AdminMenuPresenterTest test`
Expected: PASS (2 tests). Then `mvn -q compile`.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/bank/ui/view/AdminMenuView.java src/main/java/com/bank/ui/view/AdminMenuViewFx.java src/main/java/com/bank/ui/presenter/AdminMenuPresenter.java src/test/java/com/bank/ui/presenter/AdminMenuPresenterTest.java
git commit -m "feat(ui): add Admin Menu screen (view + presenter, tested)"
```

---

## Task 6: All Accounts screen

**Files:**
- Create: `src/main/java/com/bank/ui/view/AccountRow.java`, `AllAccountsView.java`, `AllAccountsViewFx.java`
- Create: `src/main/java/com/bank/ui/presenter/AllAccountsPresenter.java`
- Test: `src/test/java/com/bank/ui/presenter/AllAccountsPresenterTest.java`

**Interfaces:**
- Consumes: `AccountService`, `AdminSession`, `AdminNavigator`, `FakeAdminNavigator`.
- Produces:
  - `AccountRow` record: `AccountRow(long accountNumber, String text)`.
  - `AllAccountsView`: `showAccounts(List<AccountRow>)`, `showError(String)`, `AccountRow getSelected()` (null if none), `setOnManage(Runnable)`, `setOnBack(Runnable)`.
  - `AllAccountsPresenter(AllAccountsView, AccountService, AdminSession, AdminNavigator)`; `load()` renders rows; `manage()` reads the selected row, sets the session's selected account, navigates to Manage.

- [ ] **Step 1: Write the failing test**

`src/test/java/com/bank/ui/presenter/AllAccountsPresenterTest.java`:

```java
package com.bank.ui.presenter;

import com.bank.dao.AccountDaoJdbc;
import com.bank.dao.DaoException;
import com.bank.dao.TransactionDaoJdbc;
import com.bank.db.Database;
import com.bank.db.SchemaInitializer;
import com.bank.db.UnitOfWork;
import com.bank.model.Account;
import com.bank.model.AccountType;
import com.bank.security.PasswordHasher;
import com.bank.service.AccountService;
import com.bank.ui.AdminSession;
import com.bank.ui.FakeAdminNavigator;
import com.bank.ui.view.AccountRow;
import com.bank.ui.view.AllAccountsView;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AllAccountsPresenterTest {

    private static final Database db = Database.fromResource("db.test.properties");
    private static final UnitOfWork uow = new UnitOfWork(db);
    private final AccountService accounts =
            new AccountService(uow, new AccountDaoJdbc(), new TransactionDaoJdbc(), new PasswordHasher());

    @BeforeAll static void schema() { SchemaInitializer.initialize(db); }

    @BeforeEach
    void clean() {
        uow.executeVoid(c -> {
            try (Statement st = c.createStatement()) {
                st.execute("DELETE FROM transactions");
                st.execute("DELETE FROM accounts");
            } catch (SQLException e) {
                throw new DaoException("clean failed", e);
            }
        });
    }

    static class FakeAllAccountsView implements AllAccountsView {
        List<AccountRow> rows;
        String error;
        AccountRow selected;
        Runnable onManage = () -> {}, onBack = () -> {};
        @Override public void showAccounts(List<AccountRow> r) { rows = r; }
        @Override public void showError(String m) { error = m; }
        @Override public AccountRow getSelected() { return selected; }
        @Override public void setOnManage(Runnable h) { onManage = h; }
        @Override public void setOnBack(Runnable h) { onBack = h; }
    }

    @Test
    void loadRendersOneRowPerAccount() {
        accounts.openAccount("Asha", "1234", AccountType.SAVINGS, new BigDecimal("100.00"));
        accounts.openAccount("Ben", "5678", AccountType.CURRENT, new BigDecimal("0.00"));
        FakeAllAccountsView view = new FakeAllAccountsView();
        AllAccountsPresenter presenter =
                new AllAccountsPresenter(view, accounts, new AdminSession(), new FakeAdminNavigator());

        presenter.load();

        assertNull(view.error);
        assertEquals(2, view.rows.size());
    }

    @Test
    void manageSelectedNavigatesAndStoresSelection() {
        Account a = accounts.openAccount("Asha", "1234", AccountType.SAVINGS, new BigDecimal("100.00"));
        FakeAllAccountsView view = new FakeAllAccountsView();
        AdminSession session = new AdminSession();
        FakeAdminNavigator nav = new FakeAdminNavigator();
        AllAccountsPresenter presenter = new AllAccountsPresenter(view, accounts, session, nav);
        view.selected = new AccountRow(a.getAccountNumber(), "row");

        presenter.manage();

        assertEquals(a.getAccountNumber(), session.requireSelectedAccount());
        assertEquals(a.getAccountNumber(), nav.manageAccountShownFor);
    }

    @Test
    void manageWithNoSelectionShowsError() {
        FakeAllAccountsView view = new FakeAllAccountsView();
        AllAccountsPresenter presenter =
                new AllAccountsPresenter(view, accounts, new AdminSession(), new FakeAdminNavigator());
        view.selected = null;

        presenter.manage();

        assertEquals("Select an account first.", view.error);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q -Dtest=AllAccountsPresenterTest test`
Expected: FAIL — cannot find symbol `AccountRow` / `AllAccountsView` / `AllAccountsPresenter`.

- [ ] **Step 3: Create `AccountRow` and the `AllAccountsView` interface**

`src/main/java/com/bank/ui/view/AccountRow.java`:

```java
package com.bank.ui.view;

public record AccountRow(long accountNumber, String text) {
}
```

`src/main/java/com/bank/ui/view/AllAccountsView.java`:

```java
package com.bank.ui.view;

import java.util.List;

public interface AllAccountsView {
    void showAccounts(List<AccountRow> rows);
    void showError(String message);
    AccountRow getSelected();
    void setOnManage(Runnable handler);
    void setOnBack(Runnable handler);
}
```

- [ ] **Step 4: Create the `AllAccountsPresenter`**

`src/main/java/com/bank/ui/presenter/AllAccountsPresenter.java`:

```java
package com.bank.ui.presenter;

import com.bank.model.Account;
import com.bank.service.AccountService;
import com.bank.ui.AdminNavigator;
import com.bank.ui.AdminSession;
import com.bank.ui.view.AccountRow;
import com.bank.ui.view.AllAccountsView;

import java.util.ArrayList;
import java.util.List;

public class AllAccountsPresenter {

    private final AllAccountsView view;
    private final AccountService accountService;
    private final AdminSession session;
    private final AdminNavigator navigator;

    public AllAccountsPresenter(AllAccountsView view, AccountService accountService,
                                AdminSession session, AdminNavigator navigator) {
        this.view = view;
        this.accountService = accountService;
        this.session = session;
        this.navigator = navigator;
        view.setOnManage(this::manage);
        view.setOnBack(navigator::showAdminMenu);
    }

    public void load() {
        try {
            List<AccountRow> rows = new ArrayList<>();
            for (Account a : accountService.listAllAccounts()) {
                rows.add(new AccountRow(a.getAccountNumber(),
                        a.getAccountNumber() + "  " + a.getHolderName() + "  " + a.getAccountType()
                                + "  " + a.getBalance() + "  " + a.getStatus()));
            }
            view.showAccounts(rows);
        } catch (RuntimeException e) {
            view.showError("Something went wrong. Please try again.");
        }
    }

    public void manage() {
        AccountRow selected = view.getSelected();
        if (selected == null) {
            view.showError("Select an account first.");
            return;
        }
        session.selectAccount(selected.accountNumber());
        navigator.showManageAccount(selected.accountNumber());
    }
}
```

- [ ] **Step 5: Create the `AllAccountsViewFx`**

`src/main/java/com/bank/ui/view/AllAccountsViewFx.java`:

```java
package com.bank.ui.view;

import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.layout.VBox;

import java.util.List;

public class AllAccountsViewFx implements AllAccountsView {

    private final ListView<AccountRow> list = new ListView<>();
    private final Label errorLabel = new Label();
    private final Button manageButton = new Button("Manage Selected");
    private final Button backButton = new Button("Back");
    private final VBox root = new VBox(10);

    public AllAccountsViewFx() {
        root.setPadding(new Insets(20));
        errorLabel.setStyle("-fx-text-fill: red;");
        list.setCellFactory(lv -> new javafx.scene.control.ListCell<>() {
            @Override protected void updateItem(AccountRow item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.text());
            }
        });
        root.getChildren().addAll(new Label("All Accounts"), list, manageButton, backButton, errorLabel);
    }

    public Parent getRoot() { return root; }

    @Override public void showAccounts(List<AccountRow> rows) {
        list.setItems(FXCollections.observableArrayList(rows));
    }
    @Override public void showError(String message) { errorLabel.setText(message); }
    @Override public AccountRow getSelected() { return list.getSelectionModel().getSelectedItem(); }
    @Override public void setOnManage(Runnable handler) { manageButton.setOnAction(e -> handler.run()); }
    @Override public void setOnBack(Runnable handler) { backButton.setOnAction(e -> handler.run()); }
}
```

- [ ] **Step 6: Run test to verify it passes**

Run: `mvn -q -Dtest=AllAccountsPresenterTest test`
Expected: PASS (3 tests). Then `mvn -q compile`.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/bank/ui/view/AccountRow.java src/main/java/com/bank/ui/view/AllAccountsView.java src/main/java/com/bank/ui/view/AllAccountsViewFx.java src/main/java/com/bank/ui/presenter/AllAccountsPresenter.java src/test/java/com/bank/ui/presenter/AllAccountsPresenterTest.java
git commit -m "feat(ui): add All Accounts screen (view + presenter, tested)"
```

---

## Task 7: Admin Open Account screen

**Files:**
- Create: `src/main/java/com/bank/ui/view/AdminOpenAccountView.java`, `AdminOpenAccountViewFx.java`
- Create: `src/main/java/com/bank/ui/presenter/AdminOpenAccountPresenter.java`
- Test: `src/test/java/com/bank/ui/presenter/AdminOpenAccountPresenterTest.java`

**Interfaces:**
- Consumes: `AccountService`, `AccountType`, `AdminNavigator`, `Messages`, `FakeAdminNavigator`.
- Produces:
  - `AdminOpenAccountView`: `getHolderName()`, `getAccountType()`, `getPin()`, `getOpeningBalance()`, `showError(String)`, `showMessage(String)`, `setOnSubmit(Runnable)`, `setOnBack(Runnable)`.
  - `AdminOpenAccountPresenter(AdminOpenAccountView, AccountService, AdminNavigator)`; `submit()`.

- [ ] **Step 1: Write the failing test**

`src/test/java/com/bank/ui/presenter/AdminOpenAccountPresenterTest.java`:

```java
package com.bank.ui.presenter;

import com.bank.dao.AccountDaoJdbc;
import com.bank.dao.DaoException;
import com.bank.dao.TransactionDaoJdbc;
import com.bank.db.Database;
import com.bank.db.SchemaInitializer;
import com.bank.db.UnitOfWork;
import com.bank.security.PasswordHasher;
import com.bank.service.AccountService;
import com.bank.ui.FakeAdminNavigator;
import com.bank.ui.view.AdminOpenAccountView;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

class AdminOpenAccountPresenterTest {

    private static final Database db = Database.fromResource("db.test.properties");
    private static final UnitOfWork uow = new UnitOfWork(db);
    private final AccountService accounts =
            new AccountService(uow, new AccountDaoJdbc(), new TransactionDaoJdbc(), new PasswordHasher());

    @BeforeAll static void schema() { SchemaInitializer.initialize(db); }

    @BeforeEach
    void clean() {
        uow.executeVoid(c -> {
            try (Statement st = c.createStatement()) {
                st.execute("DELETE FROM transactions");
                st.execute("DELETE FROM accounts");
            } catch (SQLException e) {
                throw new DaoException("clean failed", e);
            }
        });
    }

    static class FakeAdminOpenAccountView implements AdminOpenAccountView {
        String name = "", type = "SAVINGS", pin = "", opening = "", error, message;
        Runnable onSubmit = () -> {}, onBack = () -> {};
        @Override public String getHolderName() { return name; }
        @Override public String getAccountType() { return type; }
        @Override public String getPin() { return pin; }
        @Override public String getOpeningBalance() { return opening; }
        @Override public void showError(String m) { error = m; }
        @Override public void showMessage(String m) { message = m; }
        @Override public void setOnSubmit(Runnable h) { onSubmit = h; }
        @Override public void setOnBack(Runnable h) { onBack = h; }
    }

    private long accountCount() {
        return uow.execute(c -> {
            try (Statement st = c.createStatement();
                 var rs = st.executeQuery("SELECT COUNT(*) FROM accounts")) {
                rs.next();
                return rs.getLong(1);
            } catch (SQLException e) { throw new DaoException("count", e); }
        });
    }

    @Test
    void validSubmitCreatesAccount() {
        FakeAdminOpenAccountView view = new FakeAdminOpenAccountView();
        AdminOpenAccountPresenter presenter =
                new AdminOpenAccountPresenter(view, accounts, new FakeAdminNavigator());
        view.name = "Asha";
        view.pin = "1234";
        view.opening = "500.00";

        presenter.submit();

        assertNull(view.error);
        assertNotNull(view.message);
        assertEquals(1, accountCount());
    }

    @Test
    void badPinShowsErrorAndCreatesNothing() {
        FakeAdminOpenAccountView view = new FakeAdminOpenAccountView();
        AdminOpenAccountPresenter presenter =
                new AdminOpenAccountPresenter(view, accounts, new FakeAdminNavigator());
        view.name = "Asha";
        view.pin = "12";
        view.opening = "500.00";

        presenter.submit();

        assertEquals("PIN must be exactly 4 digits.", view.error);
        assertEquals(0, accountCount());
    }

    @Test
    void nonNumericOpeningBalanceShowsValidationError() {
        FakeAdminOpenAccountView view = new FakeAdminOpenAccountView();
        AdminOpenAccountPresenter presenter =
                new AdminOpenAccountPresenter(view, accounts, new FakeAdminNavigator());
        view.name = "Asha";
        view.pin = "1234";
        view.opening = "abc";

        presenter.submit();

        assertEquals("Enter a valid number.", view.error);
        assertEquals(0, accountCount());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q -Dtest=AdminOpenAccountPresenterTest test`
Expected: FAIL — cannot find symbol `AdminOpenAccountView` / `AdminOpenAccountPresenter`.

- [ ] **Step 3: Create the `AdminOpenAccountView` interface**

`src/main/java/com/bank/ui/view/AdminOpenAccountView.java`:

```java
package com.bank.ui.view;

public interface AdminOpenAccountView {
    String getHolderName();
    String getAccountType();
    String getPin();
    String getOpeningBalance();
    void showError(String message);
    void showMessage(String message);
    void setOnSubmit(Runnable handler);
    void setOnBack(Runnable handler);
}
```

- [ ] **Step 4: Create the `AdminOpenAccountPresenter`**

`src/main/java/com/bank/ui/presenter/AdminOpenAccountPresenter.java`:

```java
package com.bank.ui.presenter;

import com.bank.model.Account;
import com.bank.model.AccountType;
import com.bank.service.AccountService;
import com.bank.service.BankServiceException;
import com.bank.ui.AdminNavigator;
import com.bank.ui.Messages;
import com.bank.ui.view.AdminOpenAccountView;

import java.math.BigDecimal;

public class AdminOpenAccountPresenter {

    private final AdminOpenAccountView view;
    private final AccountService accountService;

    public AdminOpenAccountPresenter(AdminOpenAccountView view, AccountService accountService,
                                     AdminNavigator navigator) {
        this.view = view;
        this.accountService = accountService;
        view.setOnSubmit(this::submit);
        view.setOnBack(navigator::showAdminMenu);
    }

    public void submit() {
        BigDecimal opening;
        try {
            opening = new BigDecimal(view.getOpeningBalance().trim());
        } catch (NumberFormatException e) {
            view.showError("Enter a valid number.");
            return;
        }
        AccountType type;
        try {
            type = AccountType.valueOf(view.getAccountType());
        } catch (IllegalArgumentException e) {
            view.showError("Select an account type.");
            return;
        }
        try {
            Account account = accountService.openAccount(view.getHolderName(), view.getPin(), type, opening);
            view.showMessage("Account created. Number: " + account.getAccountNumber());
        } catch (BankServiceException e) {
            view.showError(Messages.of(e));
        } catch (RuntimeException e) {
            view.showError("Something went wrong. Please try again.");
        }
    }
}
```

- [ ] **Step 5: Create the `AdminOpenAccountViewFx`**

`src/main/java/com/bank/ui/view/AdminOpenAccountViewFx.java`:

```java
package com.bank.ui.view;

import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;

public class AdminOpenAccountViewFx implements AdminOpenAccountView {

    private final TextField nameField = new TextField();
    private final ChoiceBox<String> typeChoice = new ChoiceBox<>();
    private final PasswordField pinField = new PasswordField();
    private final TextField openingField = new TextField();
    private final Button submitButton = new Button("Create Account");
    private final Button backButton = new Button("Back");
    private final Label errorLabel = new Label();
    private final Label messageLabel = new Label();
    private final VBox root = new VBox(10);

    public AdminOpenAccountViewFx() {
        root.setPadding(new Insets(20));
        typeChoice.getItems().addAll("SAVINGS", "CURRENT");
        typeChoice.setValue("SAVINGS");
        nameField.setPromptText("Full name");
        pinField.setPromptText("4-digit PIN");
        openingField.setPromptText("Opening balance");
        errorLabel.setStyle("-fx-text-fill: red;");
        messageLabel.setStyle("-fx-text-fill: green;");
        root.getChildren().addAll(new Label("Open Account (Admin)"), nameField, typeChoice,
                pinField, openingField, submitButton, backButton, errorLabel, messageLabel);
    }

    public Parent getRoot() { return root; }

    @Override public String getHolderName() { return nameField.getText(); }
    @Override public String getAccountType() { return typeChoice.getValue(); }
    @Override public String getPin() { return pinField.getText(); }
    @Override public String getOpeningBalance() { return openingField.getText(); }
    @Override public void showError(String message) { messageLabel.setText(""); errorLabel.setText(message); }
    @Override public void showMessage(String message) { errorLabel.setText(""); messageLabel.setText(message); }
    @Override public void setOnSubmit(Runnable handler) { submitButton.setOnAction(e -> handler.run()); }
    @Override public void setOnBack(Runnable handler) { backButton.setOnAction(e -> handler.run()); }
}
```

- [ ] **Step 6: Run test to verify it passes**

Run: `mvn -q -Dtest=AdminOpenAccountPresenterTest test`
Expected: PASS (3 tests). Then `mvn -q compile`.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/bank/ui/view/AdminOpenAccountView.java src/main/java/com/bank/ui/view/AdminOpenAccountViewFx.java src/main/java/com/bank/ui/presenter/AdminOpenAccountPresenter.java src/test/java/com/bank/ui/presenter/AdminOpenAccountPresenterTest.java
git commit -m "feat(ui): add Admin Open Account screen (view + presenter, tested)"
```

---

## Task 8: Manage Account screen

**Files:**
- Create: `src/main/java/com/bank/ui/view/ManageAccountView.java`, `ManageAccountViewFx.java`
- Create: `src/main/java/com/bank/ui/presenter/ManageAccountPresenter.java`
- Test: `src/test/java/com/bank/ui/presenter/ManageAccountPresenterTest.java`

**Interfaces:**
- Consumes: `AccountService` (`getAccount`, `accountHistory`, `blockAccount`, `closeAccount`), `AdminSession`, `AdminNavigator`, `Messages`, `Transaction`, `FakeAdminNavigator`.
- Produces:
  - `ManageAccountView`: `showDetails(String)`, `showHistory(List<String>)`, `showMessage(String)`, `showError(String)`, `setOnBlock(Runnable)`, `setOnClose(Runnable)`, `setOnBack(Runnable)`.
  - `ManageAccountPresenter(ManageAccountView, AccountService, AdminSession, AdminNavigator)`; `load()`, `block()`, `close()`.

- [ ] **Step 1: Write the failing test**

`src/test/java/com/bank/ui/presenter/ManageAccountPresenterTest.java`:

```java
package com.bank.ui.presenter;

import com.bank.dao.AccountDaoJdbc;
import com.bank.dao.DaoException;
import com.bank.dao.TransactionDaoJdbc;
import com.bank.db.Database;
import com.bank.db.SchemaInitializer;
import com.bank.db.UnitOfWork;
import com.bank.model.Account;
import com.bank.model.AccountStatus;
import com.bank.model.AccountType;
import com.bank.security.PasswordHasher;
import com.bank.service.AccountService;
import com.bank.ui.AdminSession;
import com.bank.ui.FakeAdminNavigator;
import com.bank.ui.view.ManageAccountView;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ManageAccountPresenterTest {

    private static final Database db = Database.fromResource("db.test.properties");
    private static final UnitOfWork uow = new UnitOfWork(db);
    private final AccountService accounts =
            new AccountService(uow, new AccountDaoJdbc(), new TransactionDaoJdbc(), new PasswordHasher());

    @BeforeAll static void schema() { SchemaInitializer.initialize(db); }

    @BeforeEach
    void clean() {
        uow.executeVoid(c -> {
            try (Statement st = c.createStatement()) {
                st.execute("DELETE FROM transactions");
                st.execute("DELETE FROM accounts");
            } catch (SQLException e) {
                throw new DaoException("clean failed", e);
            }
        });
    }

    static class FakeManageAccountView implements ManageAccountView {
        String details, message, error;
        List<String> history;
        Runnable onBlock = () -> {}, onClose = () -> {}, onBack = () -> {};
        @Override public void showDetails(String d) { details = d; }
        @Override public void showHistory(List<String> h) { history = h; }
        @Override public void showMessage(String m) { message = m; }
        @Override public void showError(String m) { error = m; }
        @Override public void setOnBlock(Runnable h) { onBlock = h; }
        @Override public void setOnClose(Runnable h) { onClose = h; }
        @Override public void setOnBack(Runnable h) { onBack = h; }
    }

    private AdminSession sessionFor(long acct) {
        AdminSession s = new AdminSession();
        s.selectAccount(acct);
        return s;
    }

    @Test
    void loadShowsDetailsAndHistory() {
        Account a = accounts.openAccount("Asha", "1234", AccountType.SAVINGS, new BigDecimal("100.00"));
        accounts.deposit(a.getAccountNumber(), new BigDecimal("10.00"));
        FakeManageAccountView view = new FakeManageAccountView();
        ManageAccountPresenter presenter =
                new ManageAccountPresenter(view, accounts, sessionFor(a.getAccountNumber()), new FakeAdminNavigator());

        presenter.load();

        assertNull(view.error);
        assertNotNull(view.details);
        assertTrue(view.details.contains(String.valueOf(a.getAccountNumber())));
        assertEquals(2, view.history.size()); // opening deposit + deposit
    }

    @Test
    void blockSetsStatusBlocked() {
        Account a = accounts.openAccount("Asha", "1234", AccountType.SAVINGS, new BigDecimal("100.00"));
        FakeManageAccountView view = new FakeManageAccountView();
        ManageAccountPresenter presenter =
                new ManageAccountPresenter(view, accounts, sessionFor(a.getAccountNumber()), new FakeAdminNavigator());

        presenter.block();

        assertEquals(AccountStatus.BLOCKED, accounts.getAccount(a.getAccountNumber()).getStatus());
    }

    @Test
    void closeSetsStatusClosed() {
        Account a = accounts.openAccount("Asha", "1234", AccountType.SAVINGS, new BigDecimal("100.00"));
        FakeManageAccountView view = new FakeManageAccountView();
        ManageAccountPresenter presenter =
                new ManageAccountPresenter(view, accounts, sessionFor(a.getAccountNumber()), new FakeAdminNavigator());

        presenter.close();

        assertEquals(AccountStatus.CLOSED, accounts.getAccount(a.getAccountNumber()).getStatus());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q -Dtest=ManageAccountPresenterTest test`
Expected: FAIL — cannot find symbol `ManageAccountView` / `ManageAccountPresenter`.

- [ ] **Step 3: Create the `ManageAccountView` interface**

`src/main/java/com/bank/ui/view/ManageAccountView.java`:

```java
package com.bank.ui.view;

import java.util.List;

public interface ManageAccountView {
    void showDetails(String details);
    void showHistory(List<String> lines);
    void showMessage(String message);
    void showError(String message);
    void setOnBlock(Runnable handler);
    void setOnClose(Runnable handler);
    void setOnBack(Runnable handler);
}
```

- [ ] **Step 4: Create the `ManageAccountPresenter`**

`src/main/java/com/bank/ui/presenter/ManageAccountPresenter.java`:

```java
package com.bank.ui.presenter;

import com.bank.model.Account;
import com.bank.model.Transaction;
import com.bank.service.AccountService;
import com.bank.service.BankServiceException;
import com.bank.ui.AdminNavigator;
import com.bank.ui.AdminSession;
import com.bank.ui.Messages;
import com.bank.ui.view.ManageAccountView;

import java.util.ArrayList;
import java.util.List;

public class ManageAccountPresenter {

    private final ManageAccountView view;
    private final AccountService accountService;
    private final AdminSession session;

    public ManageAccountPresenter(ManageAccountView view, AccountService accountService,
                                  AdminSession session, AdminNavigator navigator) {
        this.view = view;
        this.accountService = accountService;
        this.session = session;
        view.setOnBlock(this::block);
        view.setOnClose(this::close);
        view.setOnBack(navigator::showAllAccounts);
    }

    public void load() {
        try {
            long acct = session.requireSelectedAccount();
            Account a = accountService.getAccount(acct);
            view.showDetails("Account " + a.getAccountNumber() + "  " + a.getHolderName()
                    + "  " + a.getAccountType() + "  balance " + a.getBalance()
                    + "  status " + a.getStatus());
            List<String> lines = new ArrayList<>();
            for (Transaction t : accountService.accountHistory(acct)) {
                lines.add(t.getTimestamp() + "  " + t.getType() + "  " + t.getAmount()
                        + "  (balance " + t.getBalanceAfter() + ")");
            }
            view.showHistory(lines);
        } catch (BankServiceException e) {
            view.showError(Messages.of(e));
        } catch (RuntimeException e) {
            view.showError("Something went wrong. Please try again.");
        }
    }

    public void block() {
        applyStatus(true);
    }

    public void close() {
        applyStatus(false);
    }

    private void applyStatus(boolean block) {
        try {
            long acct = session.requireSelectedAccount();
            if (block) {
                accountService.blockAccount(acct);
                view.showMessage("Account blocked.");
            } else {
                accountService.closeAccount(acct);
                view.showMessage("Account closed.");
            }
            load();
        } catch (BankServiceException e) {
            view.showError(Messages.of(e));
        } catch (RuntimeException e) {
            view.showError("Something went wrong. Please try again.");
        }
    }
}
```

- [ ] **Step 5: Create the `ManageAccountViewFx`**

`src/main/java/com/bank/ui/view/ManageAccountViewFx.java`:

```java
package com.bank.ui.view;

import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.util.List;

public class ManageAccountViewFx implements ManageAccountView {

    private final Label detailsLabel = new Label();
    private final ListView<String> history = new ListView<>();
    private final Button blockButton = new Button("Block");
    private final Button closeButton = new Button("Close");
    private final Button backButton = new Button("Back");
    private final Label messageLabel = new Label();
    private final Label errorLabel = new Label();
    private final VBox root = new VBox(10);

    public ManageAccountViewFx() {
        root.setPadding(new Insets(20));
        messageLabel.setStyle("-fx-text-fill: green;");
        errorLabel.setStyle("-fx-text-fill: red;");
        HBox actions = new HBox(10, blockButton, closeButton, backButton);
        root.getChildren().addAll(new Label("Manage Account"), detailsLabel,
                new Label("Transactions:"), history, actions, messageLabel, errorLabel);
    }

    public Parent getRoot() { return root; }

    @Override public void showDetails(String details) { detailsLabel.setText(details); }
    @Override public void showHistory(List<String> lines) {
        history.setItems(FXCollections.observableArrayList(lines));
    }
    @Override public void showMessage(String message) { errorLabel.setText(""); messageLabel.setText(message); }
    @Override public void showError(String message) { messageLabel.setText(""); errorLabel.setText(message); }
    @Override public void setOnBlock(Runnable handler) { blockButton.setOnAction(e -> handler.run()); }
    @Override public void setOnClose(Runnable handler) { closeButton.setOnAction(e -> handler.run()); }
    @Override public void setOnBack(Runnable handler) { backButton.setOnAction(e -> handler.run()); }
}
```

- [ ] **Step 6: Run test to verify it passes**

Run: `mvn -q -Dtest=ManageAccountPresenterTest test`
Expected: PASS (3 tests). Then `mvn -q compile`.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/bank/ui/view/ManageAccountView.java src/main/java/com/bank/ui/view/ManageAccountViewFx.java src/main/java/com/bank/ui/presenter/ManageAccountPresenter.java src/test/java/com/bank/ui/presenter/ManageAccountPresenterTest.java
git commit -m "feat(ui): add Manage Account screen (view + presenter, tested)"
```

---

## Task 9: Role-Select + App wiring

**Files:**
- Create: `src/main/java/com/bank/ui/view/RoleSelectViewFx.java`
- Modify: `src/main/java/com/bank/ui/App.java`

**Interfaces:**
- Consumes: every admin view/presenter, `AdminNavigator`, `AdminSession`, `AdminService`, existing customer wiring.
- Produces: `App` now implements `AdminNavigator` too; seeds the default admin; starts at role select.

- [ ] **Step 1: Create `RoleSelectViewFx`**

`src/main/java/com/bank/ui/view/RoleSelectViewFx.java`:

```java
package com.bank.ui.view;

import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

public class RoleSelectViewFx {

    private final Button customerButton = new Button("Customer");
    private final Button adminButton = new Button("Admin");
    private final VBox root = new VBox(10);

    public RoleSelectViewFx() {
        root.setPadding(new Insets(20));
        root.getChildren().addAll(new Label("Welcome — choose a role"), customerButton, adminButton);
    }

    public Parent getRoot() { return root; }

    public void setOnCustomer(Runnable handler) { customerButton.setOnAction(e -> handler.run()); }
    public void setOnAdmin(Runnable handler) { adminButton.setOnAction(e -> handler.run()); }
}
```

- [ ] **Step 2: Modify `App.java`**

Replace the class declaration line so `App` implements both navigators:

```java
public class App extends Application implements Navigator, AdminNavigator {
```

Add these fields (alongside the existing `session`, `authService`, `accountService`):

```java
    private final AdminSession adminSession = new AdminSession();
    private AdminService adminService;
```

In `start(...)`, after the existing `accountService` is created and BEFORE `showLogin()`, add the admin service + seed, and change the initial screen to role select. The service-setup block becomes:

```java
        this.authService = new AuthService(uow, new AccountDaoJdbc(), new AdminDaoJdbc(), hasher);
        this.accountService = new AccountService(uow, new AccountDaoJdbc(), new TransactionDaoJdbc(), hasher);
        this.adminService = new AdminService(uow, new AdminDaoJdbc(), hasher);
        this.adminService.ensureDefaultAdmin();

        primaryStage.setTitle("Bank");
        showRoleSelect();
        primaryStage.show();
```

(Remove the old `showLogin();` call from `start` — `showRoleSelect()` replaces it. Keep everything else in `start` as-is.)

Add the required imports at the top:

```java
import com.bank.service.AdminService;
import com.bank.ui.presenter.AdminLoginPresenter;
import com.bank.ui.presenter.AdminMenuPresenter;
import com.bank.ui.presenter.AllAccountsPresenter;
import com.bank.ui.presenter.AdminOpenAccountPresenter;
import com.bank.ui.presenter.ManageAccountPresenter;
import com.bank.ui.view.RoleSelectViewFx;
import com.bank.ui.view.AdminLoginViewFx;
import com.bank.ui.view.AdminMenuViewFx;
import com.bank.ui.view.AllAccountsViewFx;
import com.bank.ui.view.AdminOpenAccountViewFx;
import com.bank.ui.view.ManageAccountViewFx;
```

Add the `AdminNavigator` method implementations (alongside the customer `show*` methods):

```java
    @Override
    public void showRoleSelect() {
        RoleSelectViewFx view = new RoleSelectViewFx();
        view.setOnCustomer(this::showLogin);
        view.setOnAdmin(this::showAdminLogin);
        setRoot(view.getRoot());
    }

    @Override
    public void showAdminLogin() {
        AdminLoginViewFx view = new AdminLoginViewFx();
        new AdminLoginPresenter(view, authService, this, adminSession);
        setRoot(view.getRoot());
    }

    @Override
    public void showAdminMenu() {
        AdminMenuViewFx view = new AdminMenuViewFx();
        new AdminMenuPresenter(view, this, adminSession);
        setRoot(view.getRoot());
    }

    @Override
    public void showAllAccounts() {
        AllAccountsViewFx view = new AllAccountsViewFx();
        AllAccountsPresenter presenter = new AllAccountsPresenter(view, accountService, adminSession, this);
        presenter.load();
        setRoot(view.getRoot());
    }

    @Override
    public void showAdminOpenAccount() {
        AdminOpenAccountViewFx view = new AdminOpenAccountViewFx();
        new AdminOpenAccountPresenter(view, accountService, this);
        setRoot(view.getRoot());
    }

    @Override
    public void showManageAccount(long accountNumber) {
        ManageAccountViewFx view = new ManageAccountViewFx();
        ManageAccountPresenter presenter = new ManageAccountPresenter(view, accountService, adminSession, this);
        presenter.load();
        setRoot(view.getRoot());
    }
```

(`AdminSession`, `AdminNavigator`, `AdminDaoJdbc` are already in `com.bank.ui`/imported; add imports only if the compiler complains.)

- [ ] **Step 3: Verify it compiles**

Run: `mvn -q compile`
Expected: BUILD SUCCESS. (Do NOT run `mvn javafx:run` — blocking window; the user runs it.)

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/bank/ui/view/RoleSelectViewFx.java src/main/java/com/bank/ui/App.java
git commit -m "feat(ui): add role-select landing and wire the admin flow into App"
```

---

## Task 10: Full suite + README

**Files:**
- Modify: `README.md`

**Interfaces:**
- Consumes: everything above.
- Produces: green suite + docs.

- [ ] **Step 1: Run the whole suite**

Run: `mvn test`
Expected: BUILD SUCCESS — all prior tests plus the new admin service/presenter tests pass; the existing 72 customer/service tests still pass. (No JavaFX toolkit starts.)

- [ ] **Step 2: Update `README.md`**

Replace the "**Stage 3 (current):**" line with:

```markdown
**Stage 4 (current):** Admin GUI (JavaFX, MVP) — role-select landing, admin login, list all accounts, open account, block/close, view an account's history.
Stage 3 (done): Customer ATM GUI. Stage 2 (done): service layer. Stage 1 (done): database foundation.

## Run the app
```bash
mvn javafx:run
```
Launches to a **role select** screen. Choose **Customer** for the ATM, or **Admin** and log in with the seeded default **admin / admin123** (auto-created on first run; change it in production). Requires the `bank` database and valid credentials in `src/main/resources/db.properties`; tables and the default admin are created automatically.

## Layout (added in Stage 4)
- `com.bank.ui`          — App now implements Navigator + AdminNavigator; AdminSession
- `com.bank.ui.view`     — admin views (RoleSelect, AdminLogin, AdminMenu, AllAccounts, AdminOpenAccount, ManageAccount)
- `com.bank.ui.presenter`— admin presenters (tested headless)
- `com.bank.service`     — AdminService; AccountService.listAllAccounts / getAccount / accountHistory
```

- [ ] **Step 3: Commit**

```bash
git add README.md
git commit -m "docs: document Stage 4 admin GUI"
```

---

## Self-Review Notes

- **Spec coverage:** §2 architecture (AdminNavigator/AdminSession, App implements both) → Tasks 3, 9; §3 screens → Tasks 4–8 (+ role-select in 9); §4 service additions → Tasks 1–2; §5 bootstrap (`ensureDefaultAdmin` in `App.start`) → Tasks 2, 9; §6 error handling (admin login wording; `Messages` reuse) → Tasks 4, 7, 8; §7 testing (headless presenter/service tests + `FakeAdminNavigator`) → Tasks 1–8; §8 success criteria → Task 10 full suite + README run steps. All covered.
- **Placeholder scan:** no TBD/TODO; complete code in every step.
- **Type consistency:** `AdminNavigator` methods match `FakeAdminNavigator` and `App`; `AdminSession` methods match every presenter's usage; presenter constructor signatures match their tests and the `App` wiring; `AccountRow(long, String)` record used consistently in view/presenter/test; new `AccountService` methods (`listAllAccounts`, `getAccount`, `accountHistory`) and `AdminService` (`createAdmin`, `ensureDefaultAdmin`) match callers; admin presenters import no JavaFX.
- **No customer regressions:** only additive changes (new files) plus `App` (start now goes to role-select, which routes to the unchanged `showLogin`) and three new `AccountService` methods — existing customer presenters/tests untouched.

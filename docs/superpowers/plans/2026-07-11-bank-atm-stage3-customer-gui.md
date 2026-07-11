# Stage 3 — Customer ATM GUI Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the Customer ATM desktop GUI in JavaFX as a thin MVP layer over the Stage 2 services.

**Architecture:** Model-View-Presenter. JavaFX views are dumb (no logic, no service calls) behind view interfaces; plain-Java presenters hold all logic and are unit-tested headless against the real services on `bank_test`, using fake views + a fake `Navigator`. `App` wires everything and implements the real `Navigator` (scene switching); `Session` holds the logged-in account.

**Tech Stack:** Java 17, Maven, JavaFX 21, JUnit 5, MySQL (`bank_test`), the existing `AuthService`/`AccountService`.

## Global Constraints

- Java 17; JavaFX 21 (`org.openjfx:javafx-controls:21`); `org.openjfx:javafx-maven-plugin` with `mainClass` `com.bank.ui.App` (run via `mvn javafx:run`).
- MVP boundary: **view classes contain no logic and no service calls**; **presenter classes import no JavaFX** and depend on the `Navigator` interface, never on `App`.
- Presenters catch `BankServiceException` → `Messages.of(e)`; any other `RuntimeException` (e.g. `DaoException`) → the generic message. Malformed numeric input (amount / account number) is caught and shown as "Enter a valid number." **before** any service call.
- Money is `java.math.BigDecimal`; user input strings are parsed to `BigDecimal`/`long` in the presenter.
- PIN entry uses masked fields (`PasswordField`); no plaintext PIN is displayed or logged.
- Presenter tests are headless (no JavaFX toolkit): a hand-written fake view + a shared `FakeNavigator`, run against the real services on `bank_test`, cleaning tables FK-safe (`transactions` → `accounts` → `admins`) via `UnitOfWork`.
- **Do NOT run `mvn javafx:run` in automation** — it opens a blocking window. Verify GUI/view/`App` classes with `mvn -q compile`; verify presenters/`Session`/`Messages` with `mvn test`. The running app is verified manually by the user.
- Package root `com.bank`; new UI code under `com.bank.ui`, `com.bank.ui.view`, `com.bank.ui.presenter`.

## Prerequisite (already satisfied)

`bank`/`bank_test` exist; `src/test/resources/db.test.properties` has working credentials (skip-worktree, not committed). No schema change in Stage 3.

---

## File Structure

- Modify: `pom.xml` — JavaFX dependency + plugin.
- Create: `com/bank/ui/Session.java`, `Navigator.java`, `Messages.java`, `App.java`.
- Create per screen: `com/bank/ui/view/<Screen>View.java` (interface) + `<Screen>ViewFx.java` (JavaFX) + `com/bank/ui/presenter/<Screen>Presenter.java`.
- Create test helpers: `src/test/java/com/bank/ui/FakeNavigator.java`, and per-screen `Fake<Screen>View.java` + `<Screen>PresenterTest.java`.
- Modify: `README.md` — Stage 3 run instructions.

Screens: Login, OpenAccount, Menu, Balance, Deposit, Withdraw, Transfer, MiniStatement, ChangePin.

---

## Task 1: JavaFX build + Session + Navigator + Messages

**Files:**
- Modify: `pom.xml`
- Create: `src/main/java/com/bank/ui/Session.java`, `Navigator.java`, `Messages.java`
- Test: `src/test/java/com/bank/ui/SessionTest.java`, `MessagesTest.java`

**Interfaces:**
- Consumes: the Stage 2 `BankServiceException` hierarchy.
- Produces:
  - `Session`: `void setAccount(long)`, `void clear()`, `boolean isLoggedIn()`, `long requireAccount()` (throws `IllegalStateException` if none).
  - `Navigator` interface: `showLogin()`, `showOpenAccount()`, `showMenu()`, `showBalance()`, `showDeposit()`, `showWithdraw()`, `showTransfer()`, `showMiniStatement()`, `showChangePin()`.
  - `Messages`: `static String of(BankServiceException e)`.

- [ ] **Step 1: Add JavaFX to `pom.xml`**

Add to `<dependencies>`:

```xml
        <dependency>
            <groupId>org.openjfx</groupId>
            <artifactId>javafx-controls</artifactId>
            <version>21</version>
        </dependency>
```

Add to `<build><plugins>`:

```xml
            <plugin>
                <groupId>org.openjfx</groupId>
                <artifactId>javafx-maven-plugin</artifactId>
                <version>0.0.8</version>
                <configuration>
                    <mainClass>com.bank.ui.App</mainClass>
                </configuration>
            </plugin>
```

- [ ] **Step 2: Write the failing tests**

`src/test/java/com/bank/ui/SessionTest.java`:

```java
package com.bank.ui;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SessionTest {
    @Test
    void startsLoggedOut() {
        Session s = new Session();
        assertFalse(s.isLoggedIn());
        assertThrows(IllegalStateException.class, s::requireAccount);
    }

    @Test
    void setAndClearAccount() {
        Session s = new Session();
        s.setAccount(1234567890L);
        assertTrue(s.isLoggedIn());
        assertEquals(1234567890L, s.requireAccount());
        s.clear();
        assertFalse(s.isLoggedIn());
    }
}
```

`src/test/java/com/bank/ui/MessagesTest.java`:

```java
package com.bank.ui;

import com.bank.service.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MessagesTest {
    @Test
    void mapsEachDomainException() {
        assertEquals("Invalid account number or PIN.", Messages.of(new AuthenticationException("x")));
        assertEquals("This account is blocked or closed.", Messages.of(new AccountNotActiveException("x")));
        assertEquals("Insufficient funds.", Messages.of(new InsufficientFundsException("x")));
        assertEquals("Enter a valid amount greater than zero.", Messages.of(new InvalidAmountException("x")));
        assertEquals("PIN must be exactly 4 digits.", Messages.of(new InvalidPinException("x")));
        assertEquals("Account not found.", Messages.of(new AccountNotFoundException("x")));
    }

    @Test
    void unknownSubtypeFallsBackToGeneric() {
        assertEquals("Something went wrong. Please try again.",
                Messages.of(new BankServiceException("x")));
    }
}
```

- [ ] **Step 3: Run tests to verify they fail**

Run: `mvn -q -Dtest=SessionTest,MessagesTest test`
Expected: FAIL — cannot find symbol `Session` / `Messages`.

- [ ] **Step 4: Create `Session`**

`src/main/java/com/bank/ui/Session.java`:

```java
package com.bank.ui;

public class Session {
    private Long accountNumber;

    public void setAccount(long accountNumber) {
        this.accountNumber = accountNumber;
    }

    public void clear() {
        this.accountNumber = null;
    }

    public boolean isLoggedIn() {
        return accountNumber != null;
    }

    public long requireAccount() {
        if (accountNumber == null) {
            throw new IllegalStateException("no logged-in account");
        }
        return accountNumber;
    }
}
```

- [ ] **Step 5: Create `Navigator`**

`src/main/java/com/bank/ui/Navigator.java`:

```java
package com.bank.ui;

public interface Navigator {
    void showLogin();
    void showOpenAccount();
    void showMenu();
    void showBalance();
    void showDeposit();
    void showWithdraw();
    void showTransfer();
    void showMiniStatement();
    void showChangePin();
}
```

- [ ] **Step 6: Create `Messages`**

`src/main/java/com/bank/ui/Messages.java`:

```java
package com.bank.ui;

import com.bank.service.AccountNotActiveException;
import com.bank.service.AccountNotFoundException;
import com.bank.service.AuthenticationException;
import com.bank.service.BankServiceException;
import com.bank.service.InsufficientFundsException;
import com.bank.service.InvalidAmountException;
import com.bank.service.InvalidPinException;

public final class Messages {

    private Messages() { }

    public static String of(BankServiceException e) {
        if (e instanceof AuthenticationException) {
            return "Invalid account number or PIN.";
        }
        if (e instanceof AccountNotActiveException) {
            return "This account is blocked or closed.";
        }
        if (e instanceof InsufficientFundsException) {
            return "Insufficient funds.";
        }
        if (e instanceof InvalidAmountException) {
            return "Enter a valid amount greater than zero.";
        }
        if (e instanceof InvalidPinException) {
            return "PIN must be exactly 4 digits.";
        }
        if (e instanceof AccountNotFoundException) {
            return "Account not found.";
        }
        return "Something went wrong. Please try again.";
    }
}
```

- [ ] **Step 7: Run tests to verify they pass**

Run: `mvn -q -Dtest=SessionTest,MessagesTest test`
Expected: PASS. Also run `mvn -q compile` to confirm JavaFX resolves.

- [ ] **Step 8: Commit**

```bash
git add pom.xml src/main/java/com/bank/ui/Session.java src/main/java/com/bank/ui/Navigator.java src/main/java/com/bank/ui/Messages.java src/test/java/com/bank/ui/SessionTest.java src/test/java/com/bank/ui/MessagesTest.java
git commit -m "feat(ui): add JavaFX build, Session, Navigator, Messages"
```

---

## Task 2: Login screen + shared FakeNavigator

**Files:**
- Create: `src/main/java/com/bank/ui/view/LoginView.java`, `LoginViewFx.java`
- Create: `src/main/java/com/bank/ui/presenter/LoginPresenter.java`
- Test: `src/test/java/com/bank/ui/FakeNavigator.java`, `src/test/java/com/bank/ui/presenter/LoginPresenterTest.java`

**Interfaces:**
- Consumes: `AuthService`, `AccountService` (to seed a test account), `Session`, `Navigator`, `Messages`, `Account`, `AccountType`, `UnitOfWork`, `Database`, `SchemaInitializer`, `PasswordHasher`, DAOs.
- Produces:
  - `LoginView`: `String getAccountNumber()`, `String getPin()`, `void showError(String)`, `void setOnLogin(Runnable)`, `void setOnOpenAccount(Runnable)`.
  - `LoginPresenter(LoginView, AuthService, Navigator, Session)` — registers handlers in its constructor; `void login()`.
  - `FakeNavigator` (test) recording which `show*` was called last: fields/getters `boolean loginShown`, `openAccountShown`, `menuShown`, `balanceShown`, `depositShown`, `withdrawShown`, `transferShown`, `miniStatementShown`, `changePinShown`.

- [ ] **Step 1: Write the failing test**

`src/test/java/com/bank/ui/FakeNavigator.java`:

```java
package com.bank.ui;

public class FakeNavigator implements Navigator {
    public boolean loginShown, openAccountShown, menuShown, balanceShown,
            depositShown, withdrawShown, transferShown, miniStatementShown, changePinShown;

    @Override public void showLogin() { loginShown = true; }
    @Override public void showOpenAccount() { openAccountShown = true; }
    @Override public void showMenu() { menuShown = true; }
    @Override public void showBalance() { balanceShown = true; }
    @Override public void showDeposit() { depositShown = true; }
    @Override public void showWithdraw() { withdrawShown = true; }
    @Override public void showTransfer() { transferShown = true; }
    @Override public void showMiniStatement() { miniStatementShown = true; }
    @Override public void showChangePin() { changePinShown = true; }
}
```

`src/test/java/com/bank/ui/presenter/LoginPresenterTest.java`:

```java
package com.bank.ui.presenter;

import com.bank.dao.AccountDaoJdbc;
import com.bank.dao.AdminDaoJdbc;
import com.bank.dao.DaoException;
import com.bank.dao.TransactionDaoJdbc;
import com.bank.db.Database;
import com.bank.db.SchemaInitializer;
import com.bank.db.UnitOfWork;
import com.bank.model.Account;
import com.bank.model.AccountType;
import com.bank.security.PasswordHasher;
import com.bank.service.AccountService;
import com.bank.service.AuthService;
import com.bank.ui.FakeNavigator;
import com.bank.ui.Session;
import com.bank.ui.view.LoginView;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

class LoginPresenterTest {

    private static final Database db = Database.fromResource("db.test.properties");
    private static final UnitOfWork uow = new UnitOfWork(db);
    private static final PasswordHasher hasher = new PasswordHasher();
    private final AccountService accounts =
            new AccountService(uow, new AccountDaoJdbc(), new TransactionDaoJdbc(), hasher);
    private final AuthService auth =
            new AuthService(uow, new AccountDaoJdbc(), new AdminDaoJdbc(), hasher);

    @BeforeAll
    static void schema() { SchemaInitializer.initialize(db); }

    @BeforeEach
    void clean() {
        uow.executeVoid(c -> {
            try (Statement st = c.createStatement()) {
                st.execute("DELETE FROM transactions");
                st.execute("DELETE FROM accounts");
                st.execute("DELETE FROM admins");
            } catch (SQLException e) {
                throw new DaoException("clean failed", e);
            }
        });
    }

    /** Minimal fake view returning canned inputs and recording the last error. */
    static class FakeLoginView implements LoginView {
        String account = "", pin = "", error;
        Runnable onLogin = () -> {}, onOpen = () -> {};
        @Override public String getAccountNumber() { return account; }
        @Override public String getPin() { return pin; }
        @Override public void showError(String m) { error = m; }
        @Override public void setOnLogin(Runnable h) { onLogin = h; }
        @Override public void setOnOpenAccount(Runnable h) { onOpen = h; }
    }

    private Account openAccount() {
        return accounts.openAccount("Asha", "1234", AccountType.SAVINGS, new BigDecimal("100.00"));
    }

    @Test
    void validLoginNavigatesToMenuAndSetsSession() {
        Account a = openAccount();
        FakeLoginView view = new FakeLoginView();
        Session session = new Session();
        FakeNavigator nav = new FakeNavigator();
        LoginPresenter presenter = new LoginPresenter(view, auth, nav, session);

        view.account = String.valueOf(a.getAccountNumber());
        view.pin = "1234";
        presenter.login();

        assertTrue(nav.menuShown);
        assertTrue(session.isLoggedIn());
        assertEquals(a.getAccountNumber(), session.requireAccount());
        assertNull(view.error);
    }

    @Test
    void wrongPinShowsErrorAndDoesNotNavigate() {
        Account a = openAccount();
        FakeLoginView view = new FakeLoginView();
        FakeNavigator nav = new FakeNavigator();
        LoginPresenter presenter = new LoginPresenter(view, auth, nav, new Session());

        view.account = String.valueOf(a.getAccountNumber());
        view.pin = "0000";
        presenter.login();

        assertFalse(nav.menuShown);
        assertEquals("Invalid account number or PIN.", view.error);
    }

    @Test
    void nonNumericAccountShowsValidationError() {
        FakeLoginView view = new FakeLoginView();
        FakeNavigator nav = new FakeNavigator();
        LoginPresenter presenter = new LoginPresenter(view, auth, nav, new Session());

        view.account = "abc";
        view.pin = "1234";
        presenter.login();

        assertEquals("Enter a valid number.", view.error);
        assertFalse(nav.menuShown);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q -Dtest=LoginPresenterTest test`
Expected: FAIL — cannot find symbol `LoginView` / `LoginPresenter`.

- [ ] **Step 3: Create the `LoginView` interface**

`src/main/java/com/bank/ui/view/LoginView.java`:

```java
package com.bank.ui.view;

public interface LoginView {
    String getAccountNumber();
    String getPin();
    void showError(String message);
    void setOnLogin(Runnable handler);
    void setOnOpenAccount(Runnable handler);
}
```

- [ ] **Step 4: Create the `LoginPresenter`**

`src/main/java/com/bank/ui/presenter/LoginPresenter.java`:

```java
package com.bank.ui.presenter;

import com.bank.model.Account;
import com.bank.service.AuthService;
import com.bank.service.BankServiceException;
import com.bank.ui.Messages;
import com.bank.ui.Navigator;
import com.bank.ui.Session;
import com.bank.ui.view.LoginView;

public class LoginPresenter {

    private final LoginView view;
    private final AuthService authService;
    private final Navigator navigator;
    private final Session session;

    public LoginPresenter(LoginView view, AuthService authService, Navigator navigator, Session session) {
        this.view = view;
        this.authService = authService;
        this.navigator = navigator;
        this.session = session;
        view.setOnLogin(this::login);
        view.setOnOpenAccount(navigator::showOpenAccount);
    }

    public void login() {
        long accountNumber;
        try {
            accountNumber = Long.parseLong(view.getAccountNumber().trim());
        } catch (NumberFormatException e) {
            view.showError("Enter a valid number.");
            return;
        }
        try {
            Account account = authService.authenticateCustomer(accountNumber, view.getPin());
            session.setAccount(account.getAccountNumber());
            navigator.showMenu();
        } catch (BankServiceException e) {
            view.showError(Messages.of(e));
        } catch (RuntimeException e) {
            view.showError("Something went wrong. Please try again.");
        }
    }
}
```

- [ ] **Step 5: Create the `LoginViewFx` (JavaFX view — compiled, not unit-tested)**

`src/main/java/com/bank/ui/view/LoginViewFx.java`:

```java
package com.bank.ui.view;

import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;

public class LoginViewFx implements LoginView {

    private final TextField accountField = new TextField();
    private final PasswordField pinField = new PasswordField();
    private final Button loginButton = new Button("Login");
    private final Button openButton = new Button("Open New Account");
    private final Label errorLabel = new Label();
    private final VBox root = new VBox(10);

    public LoginViewFx() {
        root.setPadding(new Insets(20));
        accountField.setPromptText("Account number");
        pinField.setPromptText("PIN");
        errorLabel.setStyle("-fx-text-fill: red;");
        root.getChildren().addAll(new Label("ATM Login"), accountField, pinField,
                loginButton, openButton, errorLabel);
    }

    public Parent getRoot() {
        return root;
    }

    @Override public String getAccountNumber() { return accountField.getText(); }
    @Override public String getPin() { return pinField.getText(); }
    @Override public void showError(String message) { errorLabel.setText(message); }
    @Override public void setOnLogin(Runnable handler) { loginButton.setOnAction(e -> handler.run()); }
    @Override public void setOnOpenAccount(Runnable handler) { openButton.setOnAction(e -> handler.run()); }
}
```

- [ ] **Step 6: Run test to verify it passes**

Run: `mvn -q -Dtest=LoginPresenterTest test`
Expected: PASS (3 tests). Then `mvn -q compile` to confirm `LoginViewFx` builds.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/bank/ui/view/LoginView.java src/main/java/com/bank/ui/view/LoginViewFx.java src/main/java/com/bank/ui/presenter/LoginPresenter.java src/test/java/com/bank/ui/FakeNavigator.java src/test/java/com/bank/ui/presenter/LoginPresenterTest.java
git commit -m "feat(ui): add Login screen (view + presenter, tested)"
```

---

## Task 3: Open Account screen

**Files:**
- Create: `src/main/java/com/bank/ui/view/OpenAccountView.java`, `OpenAccountViewFx.java`
- Create: `src/main/java/com/bank/ui/presenter/OpenAccountPresenter.java`
- Test: `src/test/java/com/bank/ui/presenter/OpenAccountPresenterTest.java`

**Interfaces:**
- Consumes: `AccountService`, `AccountType`, `Navigator`, `Messages`, `FakeNavigator`.
- Produces:
  - `OpenAccountView`: `String getHolderName()`, `String getAccountType()` (returns "SAVINGS"/"CURRENT"), `String getPin()`, `String getOpeningBalance()`, `void showError(String)`, `void showMessage(String)`, `void setOnSubmit(Runnable)`, `void setOnBack(Runnable)`.
  - `OpenAccountPresenter(OpenAccountView, AccountService, Navigator)`; `void submit()`.

- [ ] **Step 1: Write the failing test**

`src/test/java/com/bank/ui/presenter/OpenAccountPresenterTest.java`:

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
import com.bank.ui.FakeNavigator;
import com.bank.ui.view.OpenAccountView;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

class OpenAccountPresenterTest {

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

    static class FakeOpenAccountView implements OpenAccountView {
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

    @Test
    void validSubmitCreatesAccountAndShowsNumber() {
        FakeOpenAccountView view = new FakeOpenAccountView();
        OpenAccountPresenter presenter = new OpenAccountPresenter(view, accounts, new FakeNavigator());
        view.name = "Asha";
        view.type = "SAVINGS";
        view.pin = "1234";
        view.opening = "500.00";

        presenter.submit();

        assertNull(view.error);
        assertNotNull(view.message, "should show the new account number");
        long count = uow.execute(c -> {
            try (Statement st = c.createStatement();
                 var rs = st.executeQuery("SELECT COUNT(*) FROM accounts")) {
                rs.next();
                return rs.getLong(1);
            } catch (SQLException e) { throw new DaoException("count", e); }
        });
        assertEquals(1, count);
    }

    @Test
    void badPinShowsErrorAndCreatesNothing() {
        FakeOpenAccountView view = new FakeOpenAccountView();
        OpenAccountPresenter presenter = new OpenAccountPresenter(view, accounts, new FakeNavigator());
        view.name = "Asha";
        view.pin = "12";
        view.opening = "500.00";

        presenter.submit();

        assertEquals("PIN must be exactly 4 digits.", view.error);
    }

    @Test
    void nonNumericOpeningBalanceShowsValidationError() {
        FakeOpenAccountView view = new FakeOpenAccountView();
        OpenAccountPresenter presenter = new OpenAccountPresenter(view, accounts, new FakeNavigator());
        view.name = "Asha";
        view.pin = "1234";
        view.opening = "abc";

        presenter.submit();

        assertEquals("Enter a valid number.", view.error);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q -Dtest=OpenAccountPresenterTest test`
Expected: FAIL — cannot find symbol `OpenAccountView` / `OpenAccountPresenter`.

- [ ] **Step 3: Create the `OpenAccountView` interface**

`src/main/java/com/bank/ui/view/OpenAccountView.java`:

```java
package com.bank.ui.view;

public interface OpenAccountView {
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

- [ ] **Step 4: Create the `OpenAccountPresenter`**

`src/main/java/com/bank/ui/presenter/OpenAccountPresenter.java`:

```java
package com.bank.ui.presenter;

import com.bank.model.Account;
import com.bank.model.AccountType;
import com.bank.service.AccountService;
import com.bank.service.BankServiceException;
import com.bank.ui.Messages;
import com.bank.ui.Navigator;
import com.bank.ui.view.OpenAccountView;

import java.math.BigDecimal;

public class OpenAccountPresenter {

    private final OpenAccountView view;
    private final AccountService accountService;
    private final Navigator navigator;

    public OpenAccountPresenter(OpenAccountView view, AccountService accountService, Navigator navigator) {
        this.view = view;
        this.accountService = accountService;
        this.navigator = navigator;
        view.setOnSubmit(this::submit);
        view.setOnBack(navigator::showLogin);
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
            view.showMessage("Account created. Your number is " + account.getAccountNumber());
        } catch (BankServiceException e) {
            view.showError(Messages.of(e));
        } catch (RuntimeException e) {
            view.showError("Something went wrong. Please try again.");
        }
    }
}
```

- [ ] **Step 5: Create the `OpenAccountViewFx`**

`src/main/java/com/bank/ui/view/OpenAccountViewFx.java`:

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

public class OpenAccountViewFx implements OpenAccountView {

    private final TextField nameField = new TextField();
    private final ChoiceBox<String> typeChoice = new ChoiceBox<>();
    private final PasswordField pinField = new PasswordField();
    private final TextField openingField = new TextField();
    private final Button submitButton = new Button("Create Account");
    private final Button backButton = new Button("Back");
    private final Label errorLabel = new Label();
    private final Label messageLabel = new Label();
    private final VBox root = new VBox(10);

    public OpenAccountViewFx() {
        root.setPadding(new Insets(20));
        typeChoice.getItems().addAll("SAVINGS", "CURRENT");
        typeChoice.setValue("SAVINGS");
        nameField.setPromptText("Full name");
        pinField.setPromptText("4-digit PIN");
        openingField.setPromptText("Opening balance");
        errorLabel.setStyle("-fx-text-fill: red;");
        messageLabel.setStyle("-fx-text-fill: green;");
        root.getChildren().addAll(new Label("Open New Account"), nameField, typeChoice,
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

Run: `mvn -q -Dtest=OpenAccountPresenterTest test`
Expected: PASS (3 tests). Then `mvn -q compile`.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/bank/ui/view/OpenAccountView.java src/main/java/com/bank/ui/view/OpenAccountViewFx.java src/main/java/com/bank/ui/presenter/OpenAccountPresenter.java src/test/java/com/bank/ui/presenter/OpenAccountPresenterTest.java
git commit -m "feat(ui): add Open Account screen (view + presenter, tested)"
```

---

## Task 4: Menu screen (navigation + logout)

**Files:**
- Create: `src/main/java/com/bank/ui/view/MenuView.java`, `MenuViewFx.java`
- Create: `src/main/java/com/bank/ui/presenter/MenuPresenter.java`
- Test: `src/test/java/com/bank/ui/presenter/MenuPresenterTest.java`

**Interfaces:**
- Consumes: `Navigator`, `Session`, `FakeNavigator`.
- Produces:
  - `MenuView`: setters `setOnBalance`, `setOnDeposit`, `setOnWithdraw`, `setOnTransfer`, `setOnMiniStatement`, `setOnChangePin`, `setOnLogout` (each `Runnable`).
  - `MenuPresenter(MenuView, Navigator, Session)`; wires each button to the matching navigation; `logout()` clears the session then `navigator.showLogin()`.

- [ ] **Step 1: Write the failing test**

`src/test/java/com/bank/ui/presenter/MenuPresenterTest.java`:

```java
package com.bank.ui.presenter;

import com.bank.ui.FakeNavigator;
import com.bank.ui.Session;
import com.bank.ui.view.MenuView;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MenuPresenterTest {

    static class FakeMenuView implements MenuView {
        Runnable onBalance, onDeposit, onWithdraw, onTransfer, onMiniStatement, onChangePin, onLogout;
        @Override public void setOnBalance(Runnable h) { onBalance = h; }
        @Override public void setOnDeposit(Runnable h) { onDeposit = h; }
        @Override public void setOnWithdraw(Runnable h) { onWithdraw = h; }
        @Override public void setOnTransfer(Runnable h) { onTransfer = h; }
        @Override public void setOnMiniStatement(Runnable h) { onMiniStatement = h; }
        @Override public void setOnChangePin(Runnable h) { onChangePin = h; }
        @Override public void setOnLogout(Runnable h) { onLogout = h; }
    }

    @Test
    void buttonsRouteToNavigator() {
        FakeMenuView view = new FakeMenuView();
        FakeNavigator nav = new FakeNavigator();
        new MenuPresenter(view, nav, new Session());

        view.onBalance.run();
        view.onDeposit.run();
        view.onTransfer.run();

        assertTrue(nav.balanceShown);
        assertTrue(nav.depositShown);
        assertTrue(nav.transferShown);
    }

    @Test
    void logoutClearsSessionAndReturnsToLogin() {
        FakeMenuView view = new FakeMenuView();
        FakeNavigator nav = new FakeNavigator();
        Session session = new Session();
        session.setAccount(1000000001L);
        new MenuPresenter(view, nav, session);

        view.onLogout.run();

        assertFalse(session.isLoggedIn());
        assertTrue(nav.loginShown);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q -Dtest=MenuPresenterTest test`
Expected: FAIL — cannot find symbol `MenuView` / `MenuPresenter`.

- [ ] **Step 3: Create the `MenuView` interface**

`src/main/java/com/bank/ui/view/MenuView.java`:

```java
package com.bank.ui.view;

public interface MenuView {
    void setOnBalance(Runnable handler);
    void setOnDeposit(Runnable handler);
    void setOnWithdraw(Runnable handler);
    void setOnTransfer(Runnable handler);
    void setOnMiniStatement(Runnable handler);
    void setOnChangePin(Runnable handler);
    void setOnLogout(Runnable handler);
}
```

- [ ] **Step 4: Create the `MenuPresenter`**

`src/main/java/com/bank/ui/presenter/MenuPresenter.java`:

```java
package com.bank.ui.presenter;

import com.bank.ui.Navigator;
import com.bank.ui.Session;
import com.bank.ui.view.MenuView;

public class MenuPresenter {

    private final Navigator navigator;
    private final Session session;

    public MenuPresenter(MenuView view, Navigator navigator, Session session) {
        this.navigator = navigator;
        this.session = session;
        view.setOnBalance(navigator::showBalance);
        view.setOnDeposit(navigator::showDeposit);
        view.setOnWithdraw(navigator::showWithdraw);
        view.setOnTransfer(navigator::showTransfer);
        view.setOnMiniStatement(navigator::showMiniStatement);
        view.setOnChangePin(navigator::showChangePin);
        view.setOnLogout(this::logout);
    }

    public void logout() {
        session.clear();
        navigator.showLogin();
    }
}
```

- [ ] **Step 5: Create the `MenuViewFx`**

`src/main/java/com/bank/ui/view/MenuViewFx.java`:

```java
package com.bank.ui.view;

import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

public class MenuViewFx implements MenuView {

    private final Button balance = new Button("Balance");
    private final Button deposit = new Button("Deposit");
    private final Button withdraw = new Button("Withdraw");
    private final Button transfer = new Button("Transfer");
    private final Button miniStatement = new Button("Mini Statement");
    private final Button changePin = new Button("Change PIN");
    private final Button logout = new Button("Logout");
    private final VBox root = new VBox(10);

    public MenuViewFx() {
        root.setPadding(new Insets(20));
        root.getChildren().addAll(new Label("Main Menu"), balance, deposit, withdraw,
                transfer, miniStatement, changePin, logout);
    }

    public Parent getRoot() { return root; }

    @Override public void setOnBalance(Runnable h) { balance.setOnAction(e -> h.run()); }
    @Override public void setOnDeposit(Runnable h) { deposit.setOnAction(e -> h.run()); }
    @Override public void setOnWithdraw(Runnable h) { withdraw.setOnAction(e -> h.run()); }
    @Override public void setOnTransfer(Runnable h) { transfer.setOnAction(e -> h.run()); }
    @Override public void setOnMiniStatement(Runnable h) { miniStatement.setOnAction(e -> h.run()); }
    @Override public void setOnChangePin(Runnable h) { changePin.setOnAction(e -> h.run()); }
    @Override public void setOnLogout(Runnable h) { logout.setOnAction(e -> h.run()); }
}
```

- [ ] **Step 6: Run test to verify it passes**

Run: `mvn -q -Dtest=MenuPresenterTest test`
Expected: PASS (2 tests). Then `mvn -q compile`.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/bank/ui/view/MenuView.java src/main/java/com/bank/ui/view/MenuViewFx.java src/main/java/com/bank/ui/presenter/MenuPresenter.java src/test/java/com/bank/ui/presenter/MenuPresenterTest.java
git commit -m "feat(ui): add Menu screen (navigation + logout, tested)"
```

---

## Task 5: Balance screen

**Files:**
- Create: `src/main/java/com/bank/ui/view/BalanceView.java`, `BalanceViewFx.java`
- Create: `src/main/java/com/bank/ui/presenter/BalancePresenter.java`
- Test: `src/test/java/com/bank/ui/presenter/BalancePresenterTest.java`

**Interfaces:**
- Consumes: `AccountService`, `Session`, `Navigator`, `Messages`.
- Produces:
  - `BalanceView`: `void showBalance(String)`, `void showError(String)`, `void setOnBack(Runnable)`.
  - `BalancePresenter(BalanceView, AccountService, Session, Navigator)`; `void load()` reads `session.requireAccount()`, calls `getBalance`, shows it.

- [ ] **Step 1: Write the failing test**

`src/test/java/com/bank/ui/presenter/BalancePresenterTest.java`:

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
import com.bank.ui.FakeNavigator;
import com.bank.ui.Session;
import com.bank.ui.view.BalanceView;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

class BalancePresenterTest {

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

    static class FakeBalanceView implements BalanceView {
        String balance, error;
        Runnable onBack = () -> {};
        @Override public void showBalance(String b) { balance = b; }
        @Override public void showError(String m) { error = m; }
        @Override public void setOnBack(Runnable h) { onBack = h; }
    }

    @Test
    void loadShowsCurrentBalance() {
        Account a = accounts.openAccount("Asha", "1234", AccountType.SAVINGS, new BigDecimal("250.00"));
        Session session = new Session();
        session.setAccount(a.getAccountNumber());
        FakeBalanceView view = new FakeBalanceView();
        BalancePresenter presenter = new BalancePresenter(view, accounts, session, new FakeNavigator());

        presenter.load();

        assertNotNull(view.balance);
        assertTrue(view.balance.contains("250.00"), "balance text should contain the amount");
        assertNull(view.error);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q -Dtest=BalancePresenterTest test`
Expected: FAIL — cannot find symbol `BalanceView` / `BalancePresenter`.

- [ ] **Step 3: Create the `BalanceView` interface**

`src/main/java/com/bank/ui/view/BalanceView.java`:

```java
package com.bank.ui.view;

public interface BalanceView {
    void showBalance(String balance);
    void showError(String message);
    void setOnBack(Runnable handler);
}
```

- [ ] **Step 4: Create the `BalancePresenter`**

`src/main/java/com/bank/ui/presenter/BalancePresenter.java`:

```java
package com.bank.ui.presenter;

import com.bank.service.AccountService;
import com.bank.service.BankServiceException;
import com.bank.ui.Messages;
import com.bank.ui.Navigator;
import com.bank.ui.Session;
import com.bank.ui.view.BalanceView;

public class BalancePresenter {

    private final BalanceView view;
    private final AccountService accountService;
    private final Session session;

    public BalancePresenter(BalanceView view, AccountService accountService, Session session, Navigator navigator) {
        this.view = view;
        this.accountService = accountService;
        this.session = session;
        view.setOnBack(navigator::showMenu);
    }

    public void load() {
        try {
            view.showBalance("Balance: " + accountService.getBalance(session.requireAccount()));
        } catch (BankServiceException e) {
            view.showError(Messages.of(e));
        } catch (RuntimeException e) {
            view.showError("Something went wrong. Please try again.");
        }
    }
}
```

- [ ] **Step 5: Create the `BalanceViewFx`**

`src/main/java/com/bank/ui/view/BalanceViewFx.java`:

```java
package com.bank.ui.view;

import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

public class BalanceViewFx implements BalanceView {

    private final Label balanceLabel = new Label();
    private final Label errorLabel = new Label();
    private final Button backButton = new Button("Back");
    private final VBox root = new VBox(10);

    public BalanceViewFx() {
        root.setPadding(new Insets(20));
        errorLabel.setStyle("-fx-text-fill: red;");
        root.getChildren().addAll(new Label("Balance"), balanceLabel, backButton, errorLabel);
    }

    public Parent getRoot() { return root; }

    @Override public void showBalance(String balance) { balanceLabel.setText(balance); }
    @Override public void showError(String message) { errorLabel.setText(message); }
    @Override public void setOnBack(Runnable handler) { backButton.setOnAction(e -> handler.run()); }
}
```

- [ ] **Step 6: Run test to verify it passes**

Run: `mvn -q -Dtest=BalancePresenterTest test`
Expected: PASS. Then `mvn -q compile`.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/bank/ui/view/BalanceView.java src/main/java/com/bank/ui/view/BalanceViewFx.java src/main/java/com/bank/ui/presenter/BalancePresenter.java src/test/java/com/bank/ui/presenter/BalancePresenterTest.java
git commit -m "feat(ui): add Balance screen (view + presenter, tested)"
```

---

## Task 6: Deposit screen

**Files:**
- Create: `src/main/java/com/bank/ui/view/DepositView.java`, `DepositViewFx.java`
- Create: `src/main/java/com/bank/ui/presenter/DepositPresenter.java`
- Test: `src/test/java/com/bank/ui/presenter/DepositPresenterTest.java`

**Interfaces:**
- Consumes: `AccountService`, `Session`, `Navigator`, `Messages`.
- Produces:
  - `DepositView`: `String getAmount()`, `void showMessage(String)`, `void showError(String)`, `void setOnSubmit(Runnable)`, `void setOnBack(Runnable)`.
  - `DepositPresenter(DepositView, AccountService, Session, Navigator)`; `void submit()` parses amount, calls `deposit`, then shows the new balance.

- [ ] **Step 1: Write the failing test**

`src/test/java/com/bank/ui/presenter/DepositPresenterTest.java`:

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
import com.bank.ui.FakeNavigator;
import com.bank.ui.Session;
import com.bank.ui.view.DepositView;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

class DepositPresenterTest {

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

    static class FakeDepositView implements DepositView {
        String amount = "", message, error;
        Runnable onSubmit = () -> {}, onBack = () -> {};
        @Override public String getAmount() { return amount; }
        @Override public void showMessage(String m) { message = m; }
        @Override public void showError(String m) { error = m; }
        @Override public void setOnSubmit(Runnable h) { onSubmit = h; }
        @Override public void setOnBack(Runnable h) { onBack = h; }
    }

    private Session sessionFor(String opening) {
        Account a = accounts.openAccount("Asha", "1234", AccountType.SAVINGS, new BigDecimal(opening));
        Session s = new Session();
        s.setAccount(a.getAccountNumber());
        return s;
    }

    @Test
    void validDepositIncreasesBalance() {
        Session session = sessionFor("100.00");
        FakeDepositView view = new FakeDepositView();
        DepositPresenter presenter = new DepositPresenter(view, accounts, session, new FakeNavigator());
        view.amount = "50.00";

        presenter.submit();

        assertNull(view.error);
        assertEquals(0, new BigDecimal("150.00").compareTo(accounts.getBalance(session.requireAccount())));
        assertNotNull(view.message);
    }

    @Test
    void nonPositiveAmountShowsError() {
        Session session = sessionFor("100.00");
        FakeDepositView view = new FakeDepositView();
        DepositPresenter presenter = new DepositPresenter(view, accounts, session, new FakeNavigator());
        view.amount = "0";

        presenter.submit();

        assertEquals("Enter a valid amount greater than zero.", view.error);
    }

    @Test
    void nonNumericAmountShowsValidationError() {
        Session session = sessionFor("100.00");
        FakeDepositView view = new FakeDepositView();
        DepositPresenter presenter = new DepositPresenter(view, accounts, session, new FakeNavigator());
        view.amount = "abc";

        presenter.submit();

        assertEquals("Enter a valid number.", view.error);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q -Dtest=DepositPresenterTest test`
Expected: FAIL — cannot find symbol `DepositView` / `DepositPresenter`.

- [ ] **Step 3: Create the `DepositView` interface**

`src/main/java/com/bank/ui/view/DepositView.java`:

```java
package com.bank.ui.view;

public interface DepositView {
    String getAmount();
    void showMessage(String message);
    void showError(String message);
    void setOnSubmit(Runnable handler);
    void setOnBack(Runnable handler);
}
```

- [ ] **Step 4: Create the `DepositPresenter`**

`src/main/java/com/bank/ui/presenter/DepositPresenter.java`:

```java
package com.bank.ui.presenter;

import com.bank.service.AccountService;
import com.bank.service.BankServiceException;
import com.bank.ui.Messages;
import com.bank.ui.Navigator;
import com.bank.ui.Session;
import com.bank.ui.view.DepositView;

import java.math.BigDecimal;

public class DepositPresenter {

    private final DepositView view;
    private final AccountService accountService;
    private final Session session;

    public DepositPresenter(DepositView view, AccountService accountService, Session session, Navigator navigator) {
        this.view = view;
        this.accountService = accountService;
        this.session = session;
        view.setOnSubmit(this::submit);
        view.setOnBack(navigator::showMenu);
    }

    public void submit() {
        BigDecimal amount;
        try {
            amount = new BigDecimal(view.getAmount().trim());
        } catch (NumberFormatException e) {
            view.showError("Enter a valid number.");
            return;
        }
        try {
            long account = session.requireAccount();
            accountService.deposit(account, amount);
            view.showMessage("Deposited. New balance: " + accountService.getBalance(account));
        } catch (BankServiceException e) {
            view.showError(Messages.of(e));
        } catch (RuntimeException e) {
            view.showError("Something went wrong. Please try again.");
        }
    }
}
```

- [ ] **Step 5: Create the `DepositViewFx`**

`src/main/java/com/bank/ui/view/DepositViewFx.java`:

```java
package com.bank.ui.view;

import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;

public class DepositViewFx implements DepositView {

    private final TextField amountField = new TextField();
    private final Button submitButton = new Button("Deposit");
    private final Button backButton = new Button("Back");
    private final Label messageLabel = new Label();
    private final Label errorLabel = new Label();
    private final VBox root = new VBox(10);

    public DepositViewFx() {
        root.setPadding(new Insets(20));
        amountField.setPromptText("Amount");
        messageLabel.setStyle("-fx-text-fill: green;");
        errorLabel.setStyle("-fx-text-fill: red;");
        root.getChildren().addAll(new Label("Deposit"), amountField, submitButton,
                backButton, messageLabel, errorLabel);
    }

    public Parent getRoot() { return root; }

    @Override public String getAmount() { return amountField.getText(); }
    @Override public void showMessage(String message) { errorLabel.setText(""); messageLabel.setText(message); }
    @Override public void showError(String message) { messageLabel.setText(""); errorLabel.setText(message); }
    @Override public void setOnSubmit(Runnable handler) { submitButton.setOnAction(e -> handler.run()); }
    @Override public void setOnBack(Runnable handler) { backButton.setOnAction(e -> handler.run()); }
}
```

- [ ] **Step 6: Run test to verify it passes**

Run: `mvn -q -Dtest=DepositPresenterTest test`
Expected: PASS (3 tests). Then `mvn -q compile`.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/bank/ui/view/DepositView.java src/main/java/com/bank/ui/view/DepositViewFx.java src/main/java/com/bank/ui/presenter/DepositPresenter.java src/test/java/com/bank/ui/presenter/DepositPresenterTest.java
git commit -m "feat(ui): add Deposit screen (view + presenter, tested)"
```

---

## Task 7: Withdraw screen

**Files:**
- Create: `src/main/java/com/bank/ui/view/WithdrawView.java`, `WithdrawViewFx.java`
- Create: `src/main/java/com/bank/ui/presenter/WithdrawPresenter.java`
- Test: `src/test/java/com/bank/ui/presenter/WithdrawPresenterTest.java`

**Interfaces:**
- Consumes: `AccountService`, `Session`, `Navigator`, `Messages`.
- Produces:
  - `WithdrawView`: `String getAmount()`, `void showMessage(String)`, `void showError(String)`, `void setOnSubmit(Runnable)`, `void setOnBack(Runnable)`.
  - `WithdrawPresenter(WithdrawView, AccountService, Session, Navigator)`; `void submit()`.

- [ ] **Step 1: Write the failing test**

`src/test/java/com/bank/ui/presenter/WithdrawPresenterTest.java`:

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
import com.bank.ui.FakeNavigator;
import com.bank.ui.Session;
import com.bank.ui.view.WithdrawView;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

class WithdrawPresenterTest {

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

    static class FakeWithdrawView implements WithdrawView {
        String amount = "", message, error;
        Runnable onSubmit = () -> {}, onBack = () -> {};
        @Override public String getAmount() { return amount; }
        @Override public void showMessage(String m) { message = m; }
        @Override public void showError(String m) { error = m; }
        @Override public void setOnSubmit(Runnable h) { onSubmit = h; }
        @Override public void setOnBack(Runnable h) { onBack = h; }
    }

    private Session sessionFor(String opening) {
        Account a = accounts.openAccount("Asha", "1234", AccountType.SAVINGS, new BigDecimal(opening));
        Session s = new Session();
        s.setAccount(a.getAccountNumber());
        return s;
    }

    @Test
    void validWithdrawDecreasesBalance() {
        Session session = sessionFor("100.00");
        FakeWithdrawView view = new FakeWithdrawView();
        WithdrawPresenter presenter = new WithdrawPresenter(view, accounts, session, new FakeNavigator());
        view.amount = "30.00";

        presenter.submit();

        assertNull(view.error);
        assertEquals(0, new BigDecimal("70.00").compareTo(accounts.getBalance(session.requireAccount())));
    }

    @Test
    void overdraftShowsInsufficientFunds() {
        Session session = sessionFor("20.00");
        FakeWithdrawView view = new FakeWithdrawView();
        WithdrawPresenter presenter = new WithdrawPresenter(view, accounts, session, new FakeNavigator());
        view.amount = "50.00";

        presenter.submit();

        assertEquals("Insufficient funds.", view.error);
        assertEquals(0, new BigDecimal("20.00").compareTo(accounts.getBalance(session.requireAccount())));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q -Dtest=WithdrawPresenterTest test`
Expected: FAIL — cannot find symbol `WithdrawView` / `WithdrawPresenter`.

- [ ] **Step 3: Create the `WithdrawView` interface**

`src/main/java/com/bank/ui/view/WithdrawView.java`:

```java
package com.bank.ui.view;

public interface WithdrawView {
    String getAmount();
    void showMessage(String message);
    void showError(String message);
    void setOnSubmit(Runnable handler);
    void setOnBack(Runnable handler);
}
```

- [ ] **Step 4: Create the `WithdrawPresenter`**

`src/main/java/com/bank/ui/presenter/WithdrawPresenter.java`:

```java
package com.bank.ui.presenter;

import com.bank.service.AccountService;
import com.bank.service.BankServiceException;
import com.bank.ui.Messages;
import com.bank.ui.Navigator;
import com.bank.ui.Session;
import com.bank.ui.view.WithdrawView;

import java.math.BigDecimal;

public class WithdrawPresenter {

    private final WithdrawView view;
    private final AccountService accountService;
    private final Session session;

    public WithdrawPresenter(WithdrawView view, AccountService accountService, Session session, Navigator navigator) {
        this.view = view;
        this.accountService = accountService;
        this.session = session;
        view.setOnSubmit(this::submit);
        view.setOnBack(navigator::showMenu);
    }

    public void submit() {
        BigDecimal amount;
        try {
            amount = new BigDecimal(view.getAmount().trim());
        } catch (NumberFormatException e) {
            view.showError("Enter a valid number.");
            return;
        }
        try {
            long account = session.requireAccount();
            accountService.withdraw(account, amount);
            view.showMessage("Withdrew. New balance: " + accountService.getBalance(account));
        } catch (BankServiceException e) {
            view.showError(Messages.of(e));
        } catch (RuntimeException e) {
            view.showError("Something went wrong. Please try again.");
        }
    }
}
```

- [ ] **Step 5: Create the `WithdrawViewFx`**

`src/main/java/com/bank/ui/view/WithdrawViewFx.java`:

```java
package com.bank.ui.view;

import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;

public class WithdrawViewFx implements WithdrawView {

    private final TextField amountField = new TextField();
    private final Button submitButton = new Button("Withdraw");
    private final Button backButton = new Button("Back");
    private final Label messageLabel = new Label();
    private final Label errorLabel = new Label();
    private final VBox root = new VBox(10);

    public WithdrawViewFx() {
        root.setPadding(new Insets(20));
        amountField.setPromptText("Amount");
        messageLabel.setStyle("-fx-text-fill: green;");
        errorLabel.setStyle("-fx-text-fill: red;");
        root.getChildren().addAll(new Label("Withdraw"), amountField, submitButton,
                backButton, messageLabel, errorLabel);
    }

    public Parent getRoot() { return root; }

    @Override public String getAmount() { return amountField.getText(); }
    @Override public void showMessage(String message) { errorLabel.setText(""); messageLabel.setText(message); }
    @Override public void showError(String message) { messageLabel.setText(""); errorLabel.setText(message); }
    @Override public void setOnSubmit(Runnable handler) { submitButton.setOnAction(e -> handler.run()); }
    @Override public void setOnBack(Runnable handler) { backButton.setOnAction(e -> handler.run()); }
}
```

- [ ] **Step 6: Run test to verify it passes**

Run: `mvn -q -Dtest=WithdrawPresenterTest test`
Expected: PASS (2 tests). Then `mvn -q compile`.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/bank/ui/view/WithdrawView.java src/main/java/com/bank/ui/view/WithdrawViewFx.java src/main/java/com/bank/ui/presenter/WithdrawPresenter.java src/test/java/com/bank/ui/presenter/WithdrawPresenterTest.java
git commit -m "feat(ui): add Withdraw screen (view + presenter, tested)"
```

---

## Task 8: Transfer screen

**Files:**
- Create: `src/main/java/com/bank/ui/view/TransferView.java`, `TransferViewFx.java`
- Create: `src/main/java/com/bank/ui/presenter/TransferPresenter.java`
- Test: `src/test/java/com/bank/ui/presenter/TransferPresenterTest.java`

**Interfaces:**
- Consumes: `AccountService`, `Session`, `Navigator`, `Messages`.
- Produces:
  - `TransferView`: `String getTargetAccount()`, `String getAmount()`, `void showMessage(String)`, `void showError(String)`, `void setOnSubmit(Runnable)`, `void setOnBack(Runnable)`.
  - `TransferPresenter(TransferView, AccountService, Session, Navigator)`; `void submit()` parses both target account and amount, calls `transfer(session, target, amount)`.

- [ ] **Step 1: Write the failing test**

`src/test/java/com/bank/ui/presenter/TransferPresenterTest.java`:

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
import com.bank.ui.FakeNavigator;
import com.bank.ui.Session;
import com.bank.ui.view.TransferView;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

class TransferPresenterTest {

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

    static class FakeTransferView implements TransferView {
        String target = "", amount = "", message, error;
        Runnable onSubmit = () -> {}, onBack = () -> {};
        @Override public String getTargetAccount() { return target; }
        @Override public String getAmount() { return amount; }
        @Override public void showMessage(String m) { message = m; }
        @Override public void showError(String m) { error = m; }
        @Override public void setOnSubmit(Runnable h) { onSubmit = h; }
        @Override public void setOnBack(Runnable h) { onBack = h; }
    }

    @Test
    void validTransferMovesMoney() {
        Account from = accounts.openAccount("Asha", "1234", AccountType.SAVINGS, new BigDecimal("100.00"));
        Account to = accounts.openAccount("Ben", "5678", AccountType.SAVINGS, new BigDecimal("0.00"));
        Session session = new Session();
        session.setAccount(from.getAccountNumber());
        FakeTransferView view = new FakeTransferView();
        TransferPresenter presenter = new TransferPresenter(view, accounts, session, new FakeNavigator());
        view.target = String.valueOf(to.getAccountNumber());
        view.amount = "40.00";

        presenter.submit();

        assertNull(view.error);
        assertEquals(0, new BigDecimal("60.00").compareTo(accounts.getBalance(from.getAccountNumber())));
        assertEquals(0, new BigDecimal("40.00").compareTo(accounts.getBalance(to.getAccountNumber())));
    }

    @Test
    void nonNumericTargetShowsValidationError() {
        Account from = accounts.openAccount("Asha", "1234", AccountType.SAVINGS, new BigDecimal("100.00"));
        Session session = new Session();
        session.setAccount(from.getAccountNumber());
        FakeTransferView view = new FakeTransferView();
        TransferPresenter presenter = new TransferPresenter(view, accounts, session, new FakeNavigator());
        view.target = "xyz";
        view.amount = "40.00";

        presenter.submit();

        assertEquals("Enter a valid number.", view.error);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q -Dtest=TransferPresenterTest test`
Expected: FAIL — cannot find symbol `TransferView` / `TransferPresenter`.

- [ ] **Step 3: Create the `TransferView` interface**

`src/main/java/com/bank/ui/view/TransferView.java`:

```java
package com.bank.ui.view;

public interface TransferView {
    String getTargetAccount();
    String getAmount();
    void showMessage(String message);
    void showError(String message);
    void setOnSubmit(Runnable handler);
    void setOnBack(Runnable handler);
}
```

- [ ] **Step 4: Create the `TransferPresenter`**

`src/main/java/com/bank/ui/presenter/TransferPresenter.java`:

```java
package com.bank.ui.presenter;

import com.bank.service.AccountService;
import com.bank.service.BankServiceException;
import com.bank.ui.Messages;
import com.bank.ui.Navigator;
import com.bank.ui.Session;
import com.bank.ui.view.TransferView;

import java.math.BigDecimal;

public class TransferPresenter {

    private final TransferView view;
    private final AccountService accountService;
    private final Session session;

    public TransferPresenter(TransferView view, AccountService accountService, Session session, Navigator navigator) {
        this.view = view;
        this.accountService = accountService;
        this.session = session;
        view.setOnSubmit(this::submit);
        view.setOnBack(navigator::showMenu);
    }

    public void submit() {
        long target;
        BigDecimal amount;
        try {
            target = Long.parseLong(view.getTargetAccount().trim());
            amount = new BigDecimal(view.getAmount().trim());
        } catch (NumberFormatException e) {
            view.showError("Enter a valid number.");
            return;
        }
        try {
            accountService.transfer(session.requireAccount(), target, amount);
            view.showMessage("Transfer complete.");
        } catch (BankServiceException e) {
            view.showError(Messages.of(e));
        } catch (RuntimeException e) {
            view.showError("Something went wrong. Please try again.");
        }
    }
}
```

- [ ] **Step 5: Create the `TransferViewFx`**

`src/main/java/com/bank/ui/view/TransferViewFx.java`:

```java
package com.bank.ui.view;

import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;

public class TransferViewFx implements TransferView {

    private final TextField targetField = new TextField();
    private final TextField amountField = new TextField();
    private final Button submitButton = new Button("Transfer");
    private final Button backButton = new Button("Back");
    private final Label messageLabel = new Label();
    private final Label errorLabel = new Label();
    private final VBox root = new VBox(10);

    public TransferViewFx() {
        root.setPadding(new Insets(20));
        targetField.setPromptText("Target account number");
        amountField.setPromptText("Amount");
        messageLabel.setStyle("-fx-text-fill: green;");
        errorLabel.setStyle("-fx-text-fill: red;");
        root.getChildren().addAll(new Label("Transfer"), targetField, amountField,
                submitButton, backButton, messageLabel, errorLabel);
    }

    public Parent getRoot() { return root; }

    @Override public String getTargetAccount() { return targetField.getText(); }
    @Override public String getAmount() { return amountField.getText(); }
    @Override public void showMessage(String message) { errorLabel.setText(""); messageLabel.setText(message); }
    @Override public void showError(String message) { messageLabel.setText(""); errorLabel.setText(message); }
    @Override public void setOnSubmit(Runnable handler) { submitButton.setOnAction(e -> handler.run()); }
    @Override public void setOnBack(Runnable handler) { backButton.setOnAction(e -> handler.run()); }
}
```

- [ ] **Step 6: Run test to verify it passes**

Run: `mvn -q -Dtest=TransferPresenterTest test`
Expected: PASS (2 tests). Then `mvn -q compile`.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/bank/ui/view/TransferView.java src/main/java/com/bank/ui/view/TransferViewFx.java src/main/java/com/bank/ui/presenter/TransferPresenter.java src/test/java/com/bank/ui/presenter/TransferPresenterTest.java
git commit -m "feat(ui): add Transfer screen (view + presenter, tested)"
```

---

## Task 9: Mini-statement screen

**Files:**
- Create: `src/main/java/com/bank/ui/view/MiniStatementView.java`, `MiniStatementViewFx.java`
- Create: `src/main/java/com/bank/ui/presenter/MiniStatementPresenter.java`
- Test: `src/test/java/com/bank/ui/presenter/MiniStatementPresenterTest.java`

**Interfaces:**
- Consumes: `AccountService`, `Session`, `Navigator`, `Messages`, `Transaction`.
- Produces:
  - `MiniStatementView`: `void showTransactions(java.util.List<String> lines)`, `void showError(String)`, `void setOnBack(Runnable)`.
  - `MiniStatementPresenter(MiniStatementView, AccountService, Session, Navigator)`; `void load()` calls `miniStatement(account, 10)` and renders each `Transaction` to a line string.

- [ ] **Step 1: Write the failing test**

`src/test/java/com/bank/ui/presenter/MiniStatementPresenterTest.java`:

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
import com.bank.ui.FakeNavigator;
import com.bank.ui.Session;
import com.bank.ui.view.MiniStatementView;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MiniStatementPresenterTest {

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

    static class FakeMiniStatementView implements MiniStatementView {
        List<String> lines;
        String error;
        Runnable onBack = () -> {};
        @Override public void showTransactions(List<String> l) { lines = l; }
        @Override public void showError(String m) { error = m; }
        @Override public void setOnBack(Runnable h) { onBack = h; }
    }

    @Test
    void loadRendersRecentTransactions() {
        Account a = accounts.openAccount("Asha", "1234", AccountType.SAVINGS, new BigDecimal("100.00"));
        accounts.deposit(a.getAccountNumber(), new BigDecimal("20.00"));
        accounts.withdraw(a.getAccountNumber(), new BigDecimal("5.00"));
        Session session = new Session();
        session.setAccount(a.getAccountNumber());
        FakeMiniStatementView view = new FakeMiniStatementView();
        MiniStatementPresenter presenter = new MiniStatementPresenter(view, accounts, session, new FakeNavigator());

        presenter.load();

        assertNull(view.error);
        assertNotNull(view.lines);
        // opening deposit + deposit + withdraw = 3 transactions
        assertEquals(3, view.lines.size());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q -Dtest=MiniStatementPresenterTest test`
Expected: FAIL — cannot find symbol `MiniStatementView` / `MiniStatementPresenter`.

- [ ] **Step 3: Create the `MiniStatementView` interface**

`src/main/java/com/bank/ui/view/MiniStatementView.java`:

```java
package com.bank.ui.view;

import java.util.List;

public interface MiniStatementView {
    void showTransactions(List<String> lines);
    void showError(String message);
    void setOnBack(Runnable handler);
}
```

- [ ] **Step 4: Create the `MiniStatementPresenter`**

`src/main/java/com/bank/ui/presenter/MiniStatementPresenter.java`:

```java
package com.bank.ui.presenter;

import com.bank.model.Transaction;
import com.bank.service.AccountService;
import com.bank.service.BankServiceException;
import com.bank.ui.Messages;
import com.bank.ui.Navigator;
import com.bank.ui.Session;
import com.bank.ui.view.MiniStatementView;

import java.util.ArrayList;
import java.util.List;

public class MiniStatementPresenter {

    private static final int RECENT_COUNT = 10;

    private final MiniStatementView view;
    private final AccountService accountService;
    private final Session session;

    public MiniStatementPresenter(MiniStatementView view, AccountService accountService,
                                  Session session, Navigator navigator) {
        this.view = view;
        this.accountService = accountService;
        this.session = session;
        view.setOnBack(navigator::showMenu);
    }

    public void load() {
        try {
            List<Transaction> transactions =
                    accountService.miniStatement(session.requireAccount(), RECENT_COUNT);
            List<String> lines = new ArrayList<>();
            for (Transaction t : transactions) {
                lines.add(t.getTimestamp() + "  " + t.getType() + "  " + t.getAmount()
                        + "  (balance " + t.getBalanceAfter() + ")");
            }
            view.showTransactions(lines);
        } catch (BankServiceException e) {
            view.showError(Messages.of(e));
        } catch (RuntimeException e) {
            view.showError("Something went wrong. Please try again.");
        }
    }
}
```

- [ ] **Step 5: Create the `MiniStatementViewFx`**

`src/main/java/com/bank/ui/view/MiniStatementViewFx.java`:

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

public class MiniStatementViewFx implements MiniStatementView {

    private final ListView<String> list = new ListView<>();
    private final Label errorLabel = new Label();
    private final Button backButton = new Button("Back");
    private final VBox root = new VBox(10);

    public MiniStatementViewFx() {
        root.setPadding(new Insets(20));
        errorLabel.setStyle("-fx-text-fill: red;");
        root.getChildren().addAll(new Label("Mini Statement"), list, backButton, errorLabel);
    }

    public Parent getRoot() { return root; }

    @Override public void showTransactions(List<String> lines) {
        list.setItems(FXCollections.observableArrayList(lines));
    }
    @Override public void showError(String message) { errorLabel.setText(message); }
    @Override public void setOnBack(Runnable handler) { backButton.setOnAction(e -> handler.run()); }
}
```

- [ ] **Step 6: Run test to verify it passes**

Run: `mvn -q -Dtest=MiniStatementPresenterTest test`
Expected: PASS. Then `mvn -q compile`.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/bank/ui/view/MiniStatementView.java src/main/java/com/bank/ui/view/MiniStatementViewFx.java src/main/java/com/bank/ui/presenter/MiniStatementPresenter.java src/test/java/com/bank/ui/presenter/MiniStatementPresenterTest.java
git commit -m "feat(ui): add Mini-statement screen (view + presenter, tested)"
```

---

## Task 10: Change PIN screen

**Files:**
- Create: `src/main/java/com/bank/ui/view/ChangePinView.java`, `ChangePinViewFx.java`
- Create: `src/main/java/com/bank/ui/presenter/ChangePinPresenter.java`
- Test: `src/test/java/com/bank/ui/presenter/ChangePinPresenterTest.java`

**Interfaces:**
- Consumes: `AccountService`, `AuthService` (to prove the new PIN authenticates), `Session`, `Navigator`, `Messages`.
- Produces:
  - `ChangePinView`: `String getOldPin()`, `String getNewPin()`, `void showMessage(String)`, `void showError(String)`, `void setOnSubmit(Runnable)`, `void setOnBack(Runnable)`.
  - `ChangePinPresenter(ChangePinView, AccountService, Session, Navigator)`; `void submit()`.

- [ ] **Step 1: Write the failing test**

`src/test/java/com/bank/ui/presenter/ChangePinPresenterTest.java`:

```java
package com.bank.ui.presenter;

import com.bank.dao.AccountDaoJdbc;
import com.bank.dao.AdminDaoJdbc;
import com.bank.dao.DaoException;
import com.bank.dao.TransactionDaoJdbc;
import com.bank.db.Database;
import com.bank.db.SchemaInitializer;
import com.bank.db.UnitOfWork;
import com.bank.model.Account;
import com.bank.model.AccountType;
import com.bank.security.PasswordHasher;
import com.bank.service.AccountService;
import com.bank.service.AuthService;
import com.bank.ui.FakeNavigator;
import com.bank.ui.Session;
import com.bank.ui.view.ChangePinView;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

class ChangePinPresenterTest {

    private static final Database db = Database.fromResource("db.test.properties");
    private static final UnitOfWork uow = new UnitOfWork(db);
    private static final PasswordHasher hasher = new PasswordHasher();
    private final AccountService accounts =
            new AccountService(uow, new AccountDaoJdbc(), new TransactionDaoJdbc(), hasher);
    private final AuthService auth =
            new AuthService(uow, new AccountDaoJdbc(), new AdminDaoJdbc(), hasher);

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

    static class FakeChangePinView implements ChangePinView {
        String oldPin = "", newPin = "", message, error;
        Runnable onSubmit = () -> {}, onBack = () -> {};
        @Override public String getOldPin() { return oldPin; }
        @Override public String getNewPin() { return newPin; }
        @Override public void showMessage(String m) { message = m; }
        @Override public void showError(String m) { error = m; }
        @Override public void setOnSubmit(Runnable h) { onSubmit = h; }
        @Override public void setOnBack(Runnable h) { onBack = h; }
    }

    private Session login(String pin) {
        Account a = accounts.openAccount("Asha", pin, AccountType.SAVINGS, new BigDecimal("0.00"));
        Session s = new Session();
        s.setAccount(a.getAccountNumber());
        return s;
    }

    @Test
    void validChangeUpdatesPin() {
        Session session = login("1234");
        FakeChangePinView view = new FakeChangePinView();
        ChangePinPresenter presenter = new ChangePinPresenter(view, accounts, session, new FakeNavigator());
        view.oldPin = "1234";
        view.newPin = "5678";

        presenter.submit();

        assertNull(view.error);
        // new PIN now authenticates
        assertNotNull(auth.authenticateCustomer(session.requireAccount(), "5678"));
    }

    @Test
    void wrongOldPinShowsError() {
        Session session = login("1234");
        FakeChangePinView view = new FakeChangePinView();
        ChangePinPresenter presenter = new ChangePinPresenter(view, accounts, session, new FakeNavigator());
        view.oldPin = "0000";
        view.newPin = "5678";

        presenter.submit();

        assertEquals("Invalid account number or PIN.", view.error);
    }

    @Test
    void badNewPinShowsError() {
        Session session = login("1234");
        FakeChangePinView view = new FakeChangePinView();
        ChangePinPresenter presenter = new ChangePinPresenter(view, accounts, session, new FakeNavigator());
        view.oldPin = "1234";
        view.newPin = "12";

        presenter.submit();

        assertEquals("PIN must be exactly 4 digits.", view.error);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q -Dtest=ChangePinPresenterTest test`
Expected: FAIL — cannot find symbol `ChangePinView` / `ChangePinPresenter`.

- [ ] **Step 3: Create the `ChangePinView` interface**

`src/main/java/com/bank/ui/view/ChangePinView.java`:

```java
package com.bank.ui.view;

public interface ChangePinView {
    String getOldPin();
    String getNewPin();
    void showMessage(String message);
    void showError(String message);
    void setOnSubmit(Runnable handler);
    void setOnBack(Runnable handler);
}
```

- [ ] **Step 4: Create the `ChangePinPresenter`**

`src/main/java/com/bank/ui/presenter/ChangePinPresenter.java`:

```java
package com.bank.ui.presenter;

import com.bank.service.AccountService;
import com.bank.service.BankServiceException;
import com.bank.ui.Messages;
import com.bank.ui.Navigator;
import com.bank.ui.Session;
import com.bank.ui.view.ChangePinView;

public class ChangePinPresenter {

    private final ChangePinView view;
    private final AccountService accountService;
    private final Session session;

    public ChangePinPresenter(ChangePinView view, AccountService accountService, Session session, Navigator navigator) {
        this.view = view;
        this.accountService = accountService;
        this.session = session;
        view.setOnSubmit(this::submit);
        view.setOnBack(navigator::showMenu);
    }

    public void submit() {
        try {
            accountService.changePin(session.requireAccount(), view.getOldPin(), view.getNewPin());
            view.showMessage("PIN changed.");
        } catch (BankServiceException e) {
            view.showError(Messages.of(e));
        } catch (RuntimeException e) {
            view.showError("Something went wrong. Please try again.");
        }
    }
}
```

- [ ] **Step 5: Create the `ChangePinViewFx`**

`src/main/java/com/bank/ui/view/ChangePinViewFx.java`:

```java
package com.bank.ui.view;

import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.layout.VBox;

public class ChangePinViewFx implements ChangePinView {

    private final PasswordField oldPinField = new PasswordField();
    private final PasswordField newPinField = new PasswordField();
    private final Button submitButton = new Button("Change PIN");
    private final Button backButton = new Button("Back");
    private final Label messageLabel = new Label();
    private final Label errorLabel = new Label();
    private final VBox root = new VBox(10);

    public ChangePinViewFx() {
        root.setPadding(new Insets(20));
        oldPinField.setPromptText("Current PIN");
        newPinField.setPromptText("New 4-digit PIN");
        messageLabel.setStyle("-fx-text-fill: green;");
        errorLabel.setStyle("-fx-text-fill: red;");
        root.getChildren().addAll(new Label("Change PIN"), oldPinField, newPinField,
                submitButton, backButton, messageLabel, errorLabel);
    }

    public Parent getRoot() { return root; }

    @Override public String getOldPin() { return oldPinField.getText(); }
    @Override public String getNewPin() { return newPinField.getText(); }
    @Override public void showMessage(String message) { errorLabel.setText(""); messageLabel.setText(message); }
    @Override public void showError(String message) { messageLabel.setText(""); errorLabel.setText(message); }
    @Override public void setOnSubmit(Runnable handler) { submitButton.setOnAction(e -> handler.run()); }
    @Override public void setOnBack(Runnable handler) { backButton.setOnAction(e -> handler.run()); }
}
```

- [ ] **Step 6: Run test to verify it passes**

Run: `mvn -q -Dtest=ChangePinPresenterTest test`
Expected: PASS (3 tests). Then `mvn -q compile`.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/bank/ui/view/ChangePinView.java src/main/java/com/bank/ui/view/ChangePinViewFx.java src/main/java/com/bank/ui/presenter/ChangePinPresenter.java src/test/java/com/bank/ui/presenter/ChangePinPresenterTest.java
git commit -m "feat(ui): add Change PIN screen (view + presenter, tested)"
```

---

## Task 11: App wiring (JavaFX Application + real Navigator)

**Files:**
- Create: `src/main/java/com/bank/ui/App.java`

**Interfaces:**
- Consumes: every view `*ViewFx`, every presenter, `Navigator`, `Session`, `Database`, `UnitOfWork`, `PasswordHasher`, the DAOs, `AuthService`, `AccountService`, `SchemaInitializer`.
- Produces: `App extends javafx.application.Application` implementing `Navigator`; `main(String[])` launches it.

- [ ] **Step 1: Create the `App`**

`src/main/java/com/bank/ui/App.java`:

```java
package com.bank.ui;

import com.bank.dao.AccountDaoJdbc;
import com.bank.dao.AdminDaoJdbc;
import com.bank.dao.TransactionDaoJdbc;
import com.bank.db.Database;
import com.bank.db.SchemaInitializer;
import com.bank.db.UnitOfWork;
import com.bank.security.PasswordHasher;
import com.bank.service.AccountService;
import com.bank.service.AuthService;
import com.bank.ui.presenter.BalancePresenter;
import com.bank.ui.presenter.ChangePinPresenter;
import com.bank.ui.presenter.DepositPresenter;
import com.bank.ui.presenter.LoginPresenter;
import com.bank.ui.presenter.MenuPresenter;
import com.bank.ui.presenter.MiniStatementPresenter;
import com.bank.ui.presenter.OpenAccountPresenter;
import com.bank.ui.presenter.TransferPresenter;
import com.bank.ui.presenter.WithdrawPresenter;
import com.bank.ui.view.BalanceViewFx;
import com.bank.ui.view.ChangePinViewFx;
import com.bank.ui.view.DepositViewFx;
import com.bank.ui.view.LoginViewFx;
import com.bank.ui.view.MenuViewFx;
import com.bank.ui.view.MiniStatementViewFx;
import com.bank.ui.view.OpenAccountViewFx;
import com.bank.ui.view.TransferViewFx;
import com.bank.ui.view.WithdrawViewFx;
import javafx.application.Application;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class App extends Application implements Navigator {

    private Stage stage;
    private final Session session = new Session();

    private AuthService authService;
    private AccountService accountService;

    @Override
    public void start(Stage primaryStage) {
        this.stage = primaryStage;

        Database db = Database.defaultInstance();
        SchemaInitializer.initialize(db);
        UnitOfWork uow = new UnitOfWork(db);
        PasswordHasher hasher = new PasswordHasher();
        this.authService = new AuthService(uow, new AccountDaoJdbc(), new AdminDaoJdbc(), hasher);
        this.accountService = new AccountService(uow, new AccountDaoJdbc(), new TransactionDaoJdbc(), hasher);

        primaryStage.setTitle("ATM");
        showLogin();
        primaryStage.show();
    }

    private void setRoot(Parent root) {
        if (stage.getScene() == null) {
            stage.setScene(new Scene(root, 360, 480));
        } else {
            stage.getScene().setRoot(root);
        }
    }

    @Override
    public void showLogin() {
        LoginViewFx view = new LoginViewFx();
        new LoginPresenter(view, authService, this, session);
        setRoot(view.getRoot());
    }

    @Override
    public void showOpenAccount() {
        OpenAccountViewFx view = new OpenAccountViewFx();
        new OpenAccountPresenter(view, accountService, this);
        setRoot(view.getRoot());
    }

    @Override
    public void showMenu() {
        MenuViewFx view = new MenuViewFx();
        new MenuPresenter(view, this, session);
        setRoot(view.getRoot());
    }

    @Override
    public void showBalance() {
        BalanceViewFx view = new BalanceViewFx();
        BalancePresenter presenter = new BalancePresenter(view, accountService, session, this);
        presenter.load();
        setRoot(view.getRoot());
    }

    @Override
    public void showDeposit() {
        DepositViewFx view = new DepositViewFx();
        new DepositPresenter(view, accountService, session, this);
        setRoot(view.getRoot());
    }

    @Override
    public void showWithdraw() {
        WithdrawViewFx view = new WithdrawViewFx();
        new WithdrawPresenter(view, accountService, session, this);
        setRoot(view.getRoot());
    }

    @Override
    public void showTransfer() {
        TransferViewFx view = new TransferViewFx();
        new TransferPresenter(view, accountService, session, this);
        setRoot(view.getRoot());
    }

    @Override
    public void showMiniStatement() {
        MiniStatementViewFx view = new MiniStatementViewFx();
        MiniStatementPresenter presenter = new MiniStatementPresenter(view, accountService, session, this);
        presenter.load();
        setRoot(view.getRoot());
    }

    @Override
    public void showChangePin() {
        ChangePinViewFx view = new ChangePinViewFx();
        new ChangePinPresenter(view, accountService, session, this);
        setRoot(view.getRoot());
    }

    public static void main(String[] args) {
        launch(args);
    }
}
```

- [ ] **Step 2: Verify it compiles**

Run: `mvn -q compile`
Expected: BUILD SUCCESS. (Do NOT run `mvn javafx:run` here — it opens a blocking window; the user runs it manually.)

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/bank/ui/App.java
git commit -m "feat(ui): wire App with real Navigator and screen switching"
```

---

## Task 12: Full suite + README

**Files:**
- Modify: `README.md`

**Interfaces:**
- Consumes: everything above.
- Produces: green suite + run docs.

- [ ] **Step 1: Run the whole suite**

Run: `mvn test`
Expected: BUILD SUCCESS — all prior tests plus the new presenter/Session/Messages tests pass. (No JavaFX toolkit is started.)

- [ ] **Step 2: Update `README.md`**

Replace the "**Stage 2 (current):**" line with:

```markdown
**Stage 3 (current):** Customer ATM GUI (JavaFX, MVP) — login, open account, balance, deposit, withdraw, transfer, mini-statement, change PIN.
Stage 2 (done): service layer. Stage 1 (done): database foundation.

## Run the ATM app
```bash
mvn javafx:run
```
Requires the `bank` database to exist and `src/main/resources/db.properties` to have valid MySQL credentials. Tables are created automatically on first run.

## Layout (added in Stage 3)
- `com.bank.ui`          — App (JavaFX), Navigator, Session, Messages
- `com.bank.ui.view`     — thin JavaFX views + view interfaces
- `com.bank.ui.presenter`— presenters (all logic; unit-tested headless)
```

- [ ] **Step 3: Commit**

```bash
git add README.md
git commit -m "docs: document Stage 3 customer ATM GUI"
```

---

## Self-Review Notes

- **Spec coverage:** §2 MVP structure (Session/Navigator/Messages/App) → Tasks 1, 11; §2 view/presenter split → every screen task; §3 all 9 screens → Tasks 2–10; §4 Messages mapping → Task 1 (+ used everywhere); §5 build (JavaFX dep + plugin, `mvn javafx:run`) → Tasks 1, 12; §6 headless presenter tests with fake view + FakeNavigator on `bank_test` → Tasks 2–10; §7 success criteria → Task 12 full suite + README run steps. All covered.
- **Placeholder scan:** no TBD/TODO; complete code in every step.
- **Type consistency:** every presenter constructor signature matches its test's usage and the `App` wiring in Task 11; view interfaces match their fakes and `*ViewFx` impls; `Navigator` methods match `FakeNavigator` and `App`; `Messages.of` signature consistent; presenters import no JavaFX.
- **MVP boundary:** presenters depend only on view interfaces + services + `Navigator` (no JavaFX import); `*ViewFx` classes contain only widget wiring and are compile-verified, never unit-tested; `App` is the only JavaFX `Application`.
- **Headless tests:** presenter tests never start the JavaFX toolkit (fakes implement plain interfaces), so `mvn test` stays headless per the constraint.

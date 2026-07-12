# Stage 5 — Admin Dashboard Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Redesign the admin side into a dashboard — a persistent left sidebar + a swappable content area, with an accounts `TableView` + live search — reusing the existing services and Manage screen.

**Architecture:** MVP. `App` gains a dashboard shell (`BorderPane`: `SidebarFx` on the left, a content `StackPane` in the center). Admin sections render into the content region via a new `setContent(...)`, while full-screen screens (customer flow, Role Select, Admin Login) keep using `setRoot(...)`. Presenters stay JavaFX-free and unit-tested; the shell/table views are compile-verified and validated by running the app.

**Tech Stack:** Java 17, JavaFX 21, Maven, JUnit 5, MySQL (`bank_test`), the existing services. No new dependencies.

## Global Constraints

- Java 17; JavaFX 21; **no new dependencies** (no PDF library).
- MVP boundary: admin **presenters import no JavaFX** and depend on `AdminNavigator`; admin **views contain no service calls**.
- The dashboard shell lives **only in `App`**. `setContent(Parent)` renders into the dashboard's center; `setRootRaw(Parent)` makes the dashboard fill the window (no card wrapper); `setRoot(Parent)` (card-wrapped) is unchanged and used by the **customer flow, Role Select, and Admin Login**.
- The **customer flow, Role Select, and Admin Login are unchanged** (behavior + look). No customer-side edits; existing customer tests stay green.
- Presenter/logic tests are headless against real MySQL `bank_test`; clean tables FK-safe (`transactions` → `accounts` → `admins`) via `UnitOfWork`.
- **Do NOT run `mvn javafx:run` in automation** — it blocks. Verify `App`/`*ViewFx`/`SidebarFx` via `mvn -q compile`; verify presenters via `mvn test`.
- Package root `com.bank`; UI under `com.bank.ui`, `com.bank.ui.view`, `com.bank.ui.presenter`.

---

## File Structure

- Modify: `com/bank/ui/view/AccountRow.java` (expand record), `AllAccountsView.java`, `AllAccountsViewFx.java` (→ `TableView`), `com/bank/ui/presenter/AllAccountsPresenter.java` (+ `search`), `src/test/.../AllAccountsPresenterTest.java`.
- Create: `com/bank/ui/view/AdminHomeView.java`, `AdminHomeViewFx.java`, `com/bank/ui/presenter/AdminHomePresenter.java`, `src/test/.../AdminHomePresenterTest.java`.
- Create: `com/bank/ui/view/SidebarFx.java`.
- Modify: `com/bank/ui/AdminNavigator.java` (+ `showAdminHome`), `src/test/.../FakeAdminNavigator.java`, `com/bank/ui/App.java` (dashboard shell).
- Delete: `com/bank/ui/view/AdminMenuView.java`, `AdminMenuViewFx.java`, `com/bank/ui/presenter/AdminMenuPresenter.java`, `src/test/.../AdminMenuPresenterTest.java` (replaced by the sidebar dashboard).
- Modify: `src/main/resources/app.css` (sidebar + table styling), `README.md`.

---

## Task 1: All Accounts → structured table + search

**Files:**
- Modify: `src/main/java/com/bank/ui/view/AccountRow.java`, `AllAccountsView.java`, `AllAccountsViewFx.java`
- Modify: `src/main/java/com/bank/ui/presenter/AllAccountsPresenter.java`
- Modify (test): `src/test/java/com/bank/ui/presenter/AllAccountsPresenterTest.java`

**Interfaces:**
- Consumes: `AccountService.listAllAccounts`, `AdminSession`, `AdminNavigator`, `Account`.
- Produces:
  - `AccountRow(long accountNumber, String holderName, String accountType, String balance, String status)`.
  - `AllAccountsView`: `showAccounts(List<AccountRow>)`, `showError(String)`, `AccountRow getSelected()`, `setOnSearch(java.util.function.Consumer<String>)`, `setOnManage(Runnable)`.
  - `AllAccountsPresenter(AllAccountsView, AccountService, AdminSession, AdminNavigator)` with `load()`, `search(String)`, `manage()`.

- [ ] **Step 1: Update the presenter test (write first — it won't compile against the old row/interface)**

Replace `src/test/java/com/bank/ui/presenter/AllAccountsPresenterTest.java`:

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
import java.util.function.Consumer;

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
        Consumer<String> onSearch = q -> {};
        Runnable onManage = () -> {};
        @Override public void showAccounts(List<AccountRow> r) { rows = r; }
        @Override public void showError(String m) { error = m; }
        @Override public AccountRow getSelected() { return selected; }
        @Override public void setOnSearch(Consumer<String> h) { onSearch = h; }
        @Override public void setOnManage(Runnable h) { onManage = h; }
    }

    @Test
    void loadRendersOneStructuredRowPerAccount() {
        accounts.openAccount("Asha", "1234", AccountType.SAVINGS, new BigDecimal("100.00"));
        accounts.openAccount("Ben", "5678", AccountType.CURRENT, new BigDecimal("0.00"));
        FakeAllAccountsView view = new FakeAllAccountsView();
        AllAccountsPresenter presenter =
                new AllAccountsPresenter(view, accounts, new AdminSession(), new FakeAdminNavigator());

        presenter.load();

        assertNull(view.error);
        assertEquals(2, view.rows.size());
        AccountRow r = view.rows.stream().filter(x -> x.holderName().equals("Asha")).findFirst().orElseThrow();
        assertEquals("SAVINGS", r.accountType());
        assertEquals("ACTIVE", r.status());
    }

    @Test
    void searchFiltersByHolderName() {
        accounts.openAccount("Asha", "1234", AccountType.SAVINGS, new BigDecimal("100.00"));
        accounts.openAccount("Ben", "5678", AccountType.CURRENT, new BigDecimal("0.00"));
        FakeAllAccountsView view = new FakeAllAccountsView();
        AllAccountsPresenter presenter =
                new AllAccountsPresenter(view, accounts, new AdminSession(), new FakeAdminNavigator());

        presenter.search("as");

        assertEquals(1, view.rows.size());
        assertEquals("Asha", view.rows.get(0).holderName());
    }

    @Test
    void manageSelectedNavigatesAndStoresSelection() {
        Account a = accounts.openAccount("Asha", "1234", AccountType.SAVINGS, new BigDecimal("100.00"));
        FakeAllAccountsView view = new FakeAllAccountsView();
        AdminSession session = new AdminSession();
        FakeAdminNavigator nav = new FakeAdminNavigator();
        AllAccountsPresenter presenter = new AllAccountsPresenter(view, accounts, session, nav);
        view.selected = new AccountRow(a.getAccountNumber(), "Asha", "SAVINGS", "100.00", "ACTIVE");

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

- [ ] **Step 2: Run it — verify it fails to compile**

Run: `mvn -q -Dtest=AllAccountsPresenterTest test`
Expected: FAIL — `AccountRow` 5-arg constructor / `setOnSearch` don't exist yet.

- [ ] **Step 3: Expand `AccountRow`**

`src/main/java/com/bank/ui/view/AccountRow.java`:

```java
package com.bank.ui.view;

public record AccountRow(long accountNumber, String holderName, String accountType,
                         String balance, String status) {
}
```

- [ ] **Step 4: Update the `AllAccountsView` interface**

`src/main/java/com/bank/ui/view/AllAccountsView.java`:

```java
package com.bank.ui.view;

import java.util.List;
import java.util.function.Consumer;

public interface AllAccountsView {
    void showAccounts(List<AccountRow> rows);
    void showError(String message);
    AccountRow getSelected();
    void setOnSearch(Consumer<String> handler);
    void setOnManage(Runnable handler);
}
```

- [ ] **Step 5: Update the `AllAccountsPresenter`**

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
        view.setOnSearch(this::search);
        view.setOnManage(this::manage);
    }

    public void load() {
        search("");
    }

    public void search(String query) {
        try {
            String q = query == null ? "" : query.trim().toLowerCase();
            List<AccountRow> rows = new ArrayList<>();
            for (Account a : accountService.listAllAccounts()) {
                if (q.isEmpty() || a.getHolderName().toLowerCase().contains(q)) {
                    rows.add(new AccountRow(a.getAccountNumber(), a.getHolderName(),
                            a.getAccountType().name(), a.getBalance().toString(), a.getStatus().name()));
                }
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

- [ ] **Step 6: Rewrite `AllAccountsViewFx` as a `TableView`**

`src/main/java/com/bank/ui/view/AllAccountsViewFx.java`:

```java
package com.bank.ui.view;

import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.util.List;
import java.util.function.Consumer;

public class AllAccountsViewFx implements AllAccountsView {

    private final TextField searchField = new TextField();
    private final Button refreshButton = new Button("Refresh");
    private final Button manageButton = new Button("Manage Selected");
    private final TableView<AccountRow> table = new TableView<>();
    private final Label errorLabel = new Label();
    private final VBox root = new VBox(12);
    private Consumer<String> onSearch = q -> { };

    public AllAccountsViewFx() {
        TableColumn<AccountRow, String> noCol = new TableColumn<>("No");
        noCol.setCellValueFactory(cd -> new ReadOnlyStringWrapper(
                String.valueOf(table.getItems().indexOf(cd.getValue()) + 1)));
        TableColumn<AccountRow, String> acctCol = new TableColumn<>("Account #");
        acctCol.setCellValueFactory(cd -> new ReadOnlyStringWrapper(String.valueOf(cd.getValue().accountNumber())));
        TableColumn<AccountRow, String> holderCol = new TableColumn<>("Holder");
        holderCol.setCellValueFactory(cd -> new ReadOnlyStringWrapper(cd.getValue().holderName()));
        TableColumn<AccountRow, String> typeCol = new TableColumn<>("Type");
        typeCol.setCellValueFactory(cd -> new ReadOnlyStringWrapper(cd.getValue().accountType()));
        TableColumn<AccountRow, String> balCol = new TableColumn<>("Balance");
        balCol.setCellValueFactory(cd -> new ReadOnlyStringWrapper(cd.getValue().balance()));
        TableColumn<AccountRow, String> statusCol = new TableColumn<>("Status");
        statusCol.setCellValueFactory(cd -> new ReadOnlyStringWrapper(cd.getValue().status()));
        table.getColumns().add(noCol);
        table.getColumns().add(acctCol);
        table.getColumns().add(holderCol);
        table.getColumns().add(typeCol);
        table.getColumns().add(balCol);
        table.getColumns().add(statusCol);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        searchField.setPromptText("Search by name");
        searchField.textProperty().addListener((obs, oldV, newV) -> onSearch.accept(newV));
        refreshButton.setOnAction(e -> onSearch.accept(searchField.getText()));
        HBox searchBar = new HBox(10, new Label("Search by Name"), searchField, refreshButton);

        root.setPadding(new Insets(20));
        errorLabel.setStyle("-fx-text-fill: red;");
        root.getChildren().addAll(new Label("All Accounts"), searchBar, table, manageButton, errorLabel);
    }

    public Parent getRoot() { return root; }

    @Override public void showAccounts(List<AccountRow> rows) {
        table.setItems(FXCollections.observableArrayList(rows));
    }
    @Override public void showError(String message) { errorLabel.setText(message); }
    @Override public AccountRow getSelected() { return table.getSelectionModel().getSelectedItem(); }
    @Override public void setOnSearch(Consumer<String> handler) { this.onSearch = handler; }
    @Override public void setOnManage(Runnable handler) { manageButton.setOnAction(e -> handler.run()); }
}
```

- [ ] **Step 7: Run the test to verify it passes**

Run: `mvn -q -Dtest=AllAccountsPresenterTest test`
Expected: PASS (4 tests). Then `mvn -q compile` to confirm `AllAccountsViewFx` builds.

- [ ] **Step 8: Commit**

```bash
git add src/main/java/com/bank/ui/view/AccountRow.java src/main/java/com/bank/ui/view/AllAccountsView.java src/main/java/com/bank/ui/view/AllAccountsViewFx.java src/main/java/com/bank/ui/presenter/AllAccountsPresenter.java src/test/java/com/bank/ui/presenter/AllAccountsPresenterTest.java
git commit -m "feat(ui): all-accounts table with structured columns and search"
```

---

## Task 2: Home summary section

**Files:**
- Create: `src/main/java/com/bank/ui/view/AdminHomeView.java`, `AdminHomeViewFx.java`
- Create: `src/main/java/com/bank/ui/presenter/AdminHomePresenter.java`
- Test: `src/test/java/com/bank/ui/presenter/AdminHomePresenterTest.java`

**Interfaces:**
- Consumes: `AccountService.listAllAccounts`, `AccountStatus`, `Account`.
- Produces:
  - `AdminHomeView`: `void showSummary(String summary)`.
  - `AdminHomePresenter(AdminHomeView, AccountService)`; `void load()`.

- [ ] **Step 1: Write the failing test**

`src/test/java/com/bank/ui/presenter/AdminHomePresenterTest.java`:

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
import com.bank.ui.view.AdminHomeView;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

class AdminHomePresenterTest {

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

    static class FakeAdminHomeView implements AdminHomeView {
        String summary;
        @Override public void showSummary(String s) { summary = s; }
    }

    @Test
    void summaryCountsAccountsByStatus() {
        accounts.openAccount("Asha", "1234", AccountType.SAVINGS, new BigDecimal("100.00")); // ACTIVE
        Account b = accounts.openAccount("Ben", "5678", AccountType.CURRENT, new BigDecimal("0.00"));
        accounts.blockAccount(b.getAccountNumber());
        Account c = accounts.openAccount("Cara", "9012", AccountType.SAVINGS, new BigDecimal("0.00"));
        accounts.closeAccount(c.getAccountNumber());

        FakeAdminHomeView view = new FakeAdminHomeView();
        new AdminHomePresenter(view, accounts).load();

        assertNotNull(view.summary);
        assertTrue(view.summary.contains("Total accounts: 3"), view.summary);
        assertTrue(view.summary.contains("Active: 1"), view.summary);
        assertTrue(view.summary.contains("Blocked: 1"), view.summary);
        assertTrue(view.summary.contains("Closed: 1"), view.summary);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q -Dtest=AdminHomePresenterTest test`
Expected: FAIL — cannot find symbol `AdminHomeView` / `AdminHomePresenter`.

- [ ] **Step 3: Create the `AdminHomeView` interface**

`src/main/java/com/bank/ui/view/AdminHomeView.java`:

```java
package com.bank.ui.view;

public interface AdminHomeView {
    void showSummary(String summary);
}
```

- [ ] **Step 4: Create the `AdminHomePresenter`**

`src/main/java/com/bank/ui/presenter/AdminHomePresenter.java`:

```java
package com.bank.ui.presenter;

import com.bank.model.Account;
import com.bank.model.AccountStatus;
import com.bank.service.AccountService;
import com.bank.ui.view.AdminHomeView;

import java.util.List;

public class AdminHomePresenter {

    private final AdminHomeView view;
    private final AccountService accountService;

    public AdminHomePresenter(AdminHomeView view, AccountService accountService) {
        this.view = view;
        this.accountService = accountService;
    }

    public void load() {
        try {
            List<Account> all = accountService.listAllAccounts();
            long active = all.stream().filter(a -> a.getStatus() == AccountStatus.ACTIVE).count();
            long blocked = all.stream().filter(a -> a.getStatus() == AccountStatus.BLOCKED).count();
            long closed = all.stream().filter(a -> a.getStatus() == AccountStatus.CLOSED).count();
            view.showSummary("Total accounts: " + all.size()
                    + "\nActive: " + active
                    + "\nBlocked: " + blocked
                    + "\nClosed: " + closed);
        } catch (RuntimeException e) {
            view.showSummary("Unable to load summary.");
        }
    }
}
```

- [ ] **Step 5: Create the `AdminHomeViewFx`**

`src/main/java/com/bank/ui/view/AdminHomeViewFx.java`:

```java
package com.bank.ui.view;

import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

public class AdminHomeViewFx implements AdminHomeView {

    private final Label titleLabel = new Label("Dashboard — Home");
    private final Label summaryLabel = new Label();
    private final VBox root = new VBox(12);

    public AdminHomeViewFx() {
        root.setPadding(new Insets(24));
        titleLabel.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");
        root.getChildren().addAll(titleLabel, summaryLabel);
    }

    public Parent getRoot() { return root; }

    @Override public void showSummary(String summary) { summaryLabel.setText(summary); }
}
```

- [ ] **Step 6: Run test to verify it passes**

Run: `mvn -q -Dtest=AdminHomePresenterTest test`
Expected: PASS. Then `mvn -q compile`.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/bank/ui/view/AdminHomeView.java src/main/java/com/bank/ui/view/AdminHomeViewFx.java src/main/java/com/bank/ui/presenter/AdminHomePresenter.java src/test/java/com/bank/ui/presenter/AdminHomePresenterTest.java
git commit -m "feat(ui): admin home summary (account counts by status)"
```

---

## Task 3: Sidebar view

**Files:**
- Create: `src/main/java/com/bank/ui/view/SidebarFx.java`

**Interfaces:**
- Consumes: nothing (pure JavaFX view).
- Produces: `SidebarFx` with `Parent getRoot()`, `setOnHome/setOnAllAccounts/setOnOpenAccount/setOnLogout(Runnable)`, and `void setActive(String key)` (keys: `"home"`, `"accounts"`, `"open"`).

- [ ] **Step 1: Create `SidebarFx`**

`src/main/java/com/bank/ui/view/SidebarFx.java`:

```java
package com.bank.ui.view;

import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

public class SidebarFx {

    private final Button homeButton = new Button("Home");
    private final Button allAccountsButton = new Button("All Accounts");
    private final Button openAccountButton = new Button("Open Account");
    private final Button logoutButton = new Button("Log Out");
    private final VBox root = new VBox(6);

    public SidebarFx() {
        root.getStyleClass().add("sidebar");
        root.setPrefWidth(200);
        Label brand = new Label("Sky Bank");
        brand.getStyleClass().add("brand");
        for (Button b : new Button[]{homeButton, allAccountsButton, openAccountButton, logoutButton}) {
            b.getStyleClass().add("sidebar-item");
            b.setMaxWidth(Double.MAX_VALUE);
        }
        root.getChildren().addAll(brand, homeButton, allAccountsButton, openAccountButton, logoutButton);
    }

    public Parent getRoot() { return root; }

    public void setOnHome(Runnable h) { homeButton.setOnAction(e -> h.run()); }
    public void setOnAllAccounts(Runnable h) { allAccountsButton.setOnAction(e -> h.run()); }
    public void setOnOpenAccount(Runnable h) { openAccountButton.setOnAction(e -> h.run()); }
    public void setOnLogout(Runnable h) { logoutButton.setOnAction(e -> h.run()); }

    public void setActive(String key) {
        homeButton.getStyleClass().remove("active");
        allAccountsButton.getStyleClass().remove("active");
        openAccountButton.getStyleClass().remove("active");
        Button target = switch (key) {
            case "home" -> homeButton;
            case "accounts" -> allAccountsButton;
            case "open" -> openAccountButton;
            default -> null;
        };
        if (target != null && !target.getStyleClass().contains("active")) {
            target.getStyleClass().add("active");
        }
    }
}
```

- [ ] **Step 2: Verify it compiles**

Run: `mvn -q compile`
Expected: BUILD SUCCESS.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/bank/ui/view/SidebarFx.java
git commit -m "feat(ui): add dashboard SidebarFx"
```

---

## Task 4: Dashboard wiring in App

**Files:**
- Modify: `src/main/java/com/bank/ui/AdminNavigator.java`
- Modify (test helper): `src/test/java/com/bank/ui/FakeAdminNavigator.java`
- Modify: `src/main/java/com/bank/ui/App.java`
- Delete: `src/main/java/com/bank/ui/view/AdminMenuView.java`, `AdminMenuViewFx.java`, `src/main/java/com/bank/ui/presenter/AdminMenuPresenter.java`, `src/test/java/com/bank/ui/presenter/AdminMenuPresenterTest.java`
- Modify: `src/main/resources/app.css`

**Interfaces:**
- Consumes: `SidebarFx`, `AdminHomeViewFx`/`AdminHomePresenter`, `AllAccountsViewFx`/`AllAccountsPresenter`, `AdminOpenAccountViewFx`/`AdminOpenAccountPresenter`, `ManageAccountViewFx`/`ManageAccountPresenter`, `AccountService`, `AdminSession`.
- Produces: `AdminNavigator` gains `void showAdminHome()`. `App` builds the dashboard shell.

- [ ] **Step 1: Add `showAdminHome()` to `AdminNavigator`**

`src/main/java/com/bank/ui/AdminNavigator.java` — add the method:

```java
package com.bank.ui;

public interface AdminNavigator {
    void showRoleSelect();
    void showAdminLogin();
    void showAdminMenu();
    void showAdminHome();
    void showAllAccounts();
    void showAdminOpenAccount();
    void showManageAccount(long accountNumber);
}
```

- [ ] **Step 2: Update `FakeAdminNavigator`**

`src/test/java/com/bank/ui/FakeAdminNavigator.java`:

```java
package com.bank.ui;

public class FakeAdminNavigator implements AdminNavigator {
    public boolean roleSelectShown, adminLoginShown, adminMenuShown, adminHomeShown,
            allAccountsShown, adminOpenAccountShown;
    public Long manageAccountShownFor;

    @Override public void showRoleSelect() { roleSelectShown = true; }
    @Override public void showAdminLogin() { adminLoginShown = true; }
    @Override public void showAdminMenu() { adminMenuShown = true; }
    @Override public void showAdminHome() { adminHomeShown = true; }
    @Override public void showAllAccounts() { allAccountsShown = true; }
    @Override public void showAdminOpenAccount() { adminOpenAccountShown = true; }
    @Override public void showManageAccount(long accountNumber) { manageAccountShownFor = accountNumber; }
}
```

- [ ] **Step 3: Delete the obsolete Admin Menu files**

The button-list admin menu is replaced by the sidebar dashboard.

```bash
git rm src/main/java/com/bank/ui/view/AdminMenuView.java \
       src/main/java/com/bank/ui/view/AdminMenuViewFx.java \
       src/main/java/com/bank/ui/presenter/AdminMenuPresenter.java \
       src/test/java/com/bank/ui/presenter/AdminMenuPresenterTest.java
```

- [ ] **Step 4: Rewrite `App.java`**

`src/main/java/com/bank/ui/App.java` (replaces the whole file — the customer `show*` methods and services are unchanged; the admin section changes to render into the dashboard content):

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
import com.bank.service.AdminService;
import com.bank.service.AuthService;
import com.bank.ui.presenter.AdminHomePresenter;
import com.bank.ui.presenter.AdminLoginPresenter;
import com.bank.ui.presenter.AdminOpenAccountPresenter;
import com.bank.ui.presenter.AllAccountsPresenter;
import com.bank.ui.presenter.BalancePresenter;
import com.bank.ui.presenter.ChangePinPresenter;
import com.bank.ui.presenter.DepositPresenter;
import com.bank.ui.presenter.LoginPresenter;
import com.bank.ui.presenter.ManageAccountPresenter;
import com.bank.ui.presenter.MenuPresenter;
import com.bank.ui.presenter.MiniStatementPresenter;
import com.bank.ui.presenter.OpenAccountPresenter;
import com.bank.ui.presenter.TransferPresenter;
import com.bank.ui.presenter.WithdrawPresenter;
import com.bank.ui.view.AdminHomeViewFx;
import com.bank.ui.view.AdminLoginViewFx;
import com.bank.ui.view.AdminOpenAccountViewFx;
import com.bank.ui.view.AllAccountsViewFx;
import com.bank.ui.view.BalanceViewFx;
import com.bank.ui.view.ChangePinViewFx;
import com.bank.ui.view.DepositViewFx;
import com.bank.ui.view.LoginViewFx;
import com.bank.ui.view.ManageAccountViewFx;
import com.bank.ui.view.MenuViewFx;
import com.bank.ui.view.MiniStatementViewFx;
import com.bank.ui.view.OpenAccountViewFx;
import com.bank.ui.view.RoleSelectViewFx;
import com.bank.ui.view.SidebarFx;
import com.bank.ui.view.TransferViewFx;
import com.bank.ui.view.WithdrawViewFx;
import javafx.application.Application;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

public class App extends Application implements Navigator, AdminNavigator {

    private Stage stage;
    private final Session session = new Session();
    private final AdminSession adminSession = new AdminSession();

    private AuthService authService;
    private AccountService accountService;
    private AdminService adminService;

    private SidebarFx sidebar;
    private StackPane contentPane;

    @Override
    public void start(Stage primaryStage) {
        this.stage = primaryStage;

        Database db = Database.defaultInstance();
        SchemaInitializer.initialize(db);
        UnitOfWork uow = new UnitOfWork(db);
        PasswordHasher hasher = new PasswordHasher();
        this.authService = new AuthService(uow, new AccountDaoJdbc(), new AdminDaoJdbc(), hasher);
        this.accountService = new AccountService(uow, new AccountDaoJdbc(), new TransactionDaoJdbc(), hasher);
        this.adminService = new AdminService(uow, new AdminDaoJdbc(), hasher);
        this.adminService.ensureDefaultAdmin();

        primaryStage.setTitle("Bank");
        showRoleSelect();
        primaryStage.show();
    }

    // ---- root/content placement ----

    private void applyRoot(Parent root) {
        if (stage.getScene() == null) {
            Scene scene = new Scene(root, 940, 620);
            scene.getStylesheets().add(getClass().getResource("/app.css").toExternalForm());
            stage.setScene(scene);
        } else {
            stage.getScene().setRoot(root);
        }
    }

    /** Full-screen card-wrapped screens (customer flow, role select, admin login). */
    private void setRoot(Parent root) {
        root.getStyleClass().add("card");
        StackPane background = new StackPane(root);
        background.getStyleClass().add("app-background");
        applyRoot(background);
    }

    /** Dashboard shell — fills the window, no card wrapper. */
    private void setRootRaw(Parent root) {
        applyRoot(root);
    }

    /** Render a section into the dashboard content region (sidebar stays). */
    private void setContent(Parent node) {
        contentPane.getChildren().setAll(node);
    }

    // ---- customer flow (unchanged) ----

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

    // ---- role select + admin login (full screen) ----

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

    // ---- admin dashboard ----

    @Override
    public void showAdminMenu() {
        sidebar = new SidebarFx();
        sidebar.setOnHome(this::showAdminHome);
        sidebar.setOnAllAccounts(this::showAllAccounts);
        sidebar.setOnOpenAccount(this::showAdminOpenAccount);
        sidebar.setOnLogout(() -> {
            adminSession.clear();
            showRoleSelect();
        });
        contentPane = new StackPane();
        contentPane.getStyleClass().add("content-pane");
        BorderPane dashboard = new BorderPane();
        dashboard.setLeft(sidebar.getRoot());
        dashboard.setCenter(contentPane);
        setRootRaw(dashboard);
        showAdminHome();
    }

    @Override
    public void showAdminHome() {
        AdminHomeViewFx view = new AdminHomeViewFx();
        new AdminHomePresenter(view, accountService).load();
        sidebar.setActive("home");
        setContent(view.getRoot());
    }

    @Override
    public void showAllAccounts() {
        AllAccountsViewFx view = new AllAccountsViewFx();
        AllAccountsPresenter presenter = new AllAccountsPresenter(view, accountService, adminSession, this);
        presenter.load();
        sidebar.setActive("accounts");
        setContent(view.getRoot());
    }

    @Override
    public void showAdminOpenAccount() {
        AdminOpenAccountViewFx view = new AdminOpenAccountViewFx();
        new AdminOpenAccountPresenter(view, accountService, this);
        sidebar.setActive("open");
        setContent(view.getRoot());
    }

    @Override
    public void showManageAccount(long accountNumber) {
        ManageAccountViewFx view = new ManageAccountViewFx();
        ManageAccountPresenter presenter = new ManageAccountPresenter(view, accountService, adminSession, this);
        presenter.load();
        sidebar.setActive("accounts");
        setContent(view.getRoot());
    }

    public static void main(String[] args) {
        launch(args);
    }
}
```

- [ ] **Step 5: Add sidebar + table styling to `app.css`**

Append to `src/main/resources/app.css`:

```css
/* ---- admin dashboard ---- */
.sidebar {
    -fx-background-color: linear-gradient(to bottom, #4a90e2, #2f6fc0);
    -fx-padding: 16 10 16 10;
}
.sidebar .brand {
    -fx-text-fill: white;
    -fx-font-size: 18px;
    -fx-font-weight: bold;
    -fx-padding: 8 0 16 6;
}
.sidebar-item {
    -fx-background-color: transparent;
    -fx-text-fill: white;
    -fx-font-size: 13px;
    -fx-font-weight: normal;
    -fx-alignment: center-left;
    -fx-padding: 10 14 10 14;
    -fx-background-radius: 8;
    -fx-effect: null;
    -fx-cursor: hand;
}
.sidebar-item:hover  { -fx-background-color: rgba(255, 255, 255, 0.18); }
.sidebar-item.active { -fx-background-color: #2ecc71; -fx-font-weight: bold; }

.content-pane { -fx-background-color: #f4f6fb; }

.table-view { -fx-background-radius: 8; }
.table-view .column-header-background { -fx-background-color: #4a90e2; }
.table-view .column-header .label { -fx-text-fill: white; -fx-font-weight: bold; }
.table-row-cell:selected { -fx-background-color: #d6e4ff; -fx-text-fill: #14315e; }
```

- [ ] **Step 6: Verify it compiles**

Run: `mvn -q compile`
Expected: BUILD SUCCESS. (Do NOT run `mvn javafx:run` — blocking; the user runs it.)

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/bank/ui/AdminNavigator.java src/test/java/com/bank/ui/FakeAdminNavigator.java src/main/java/com/bank/ui/App.java src/main/resources/app.css
git commit -m "feat(ui): wire admin dashboard shell (sidebar + content) into App"
```

---

## Task 5: Full suite + README

**Files:**
- Modify: `README.md`

**Interfaces:**
- Consumes: everything above.
- Produces: green suite + docs.

- [ ] **Step 1: Run the whole suite**

Run: `mvn test`
Expected: BUILD SUCCESS — the customer tests, the service tests, and the admin presenter tests (including the updated All Accounts + new Home) pass. The removed `AdminMenuPresenterTest` is gone; no other test references it.

- [ ] **Step 2: Update `README.md`**

Replace the "**Stage 4 (current):**" / most-recent status line with:

```markdown
**Admin dashboard (current):** after admin login, a sidebar dashboard — Home (account summary), All Accounts (searchable table), Open Account — with the account Manage panel (block/close/unblock/reopen/delete/history) in the content area.
Stages 1–4 (done): DB foundation, service layer, customer ATM GUI, admin GUI.

## Run the app
```bash
mvn javafx:run
```
Role select → **Admin** → log in `admin` / `admin123` → dashboard. **Customer** → the ATM flow (unchanged).
```

- [ ] **Step 3: Commit**

```bash
git add README.md
git commit -m "docs: document the admin dashboard"
```

---

## Self-Review Notes

- **Spec coverage:** §2 shell (App setContent/setRootRaw, showAdminMenu→dashboard) → Task 4; §3 sidebar → Task 3 (+ wiring Task 4); §4 Home → Task 2; §5 accounts table + search → Task 1; §6 Manage panel (render via setContent) → Task 4 `showManageAccount`; §7 styling → Task 4 css; §8 testing (Home + search presenter tests, ManageAccount unchanged) → Tasks 1–2; §9 success criteria → Task 5 suite + README. All covered.
- **Placeholder scan:** no TBD/TODO; complete code in every step.
- **Type consistency:** `AccountRow(long,String,String,String,String)` used identically in the record, `AllAccountsViewFx`, `AllAccountsPresenter`, and the test; `AdminNavigator.showAdminHome` matches `FakeAdminNavigator` and `App`; `AllAccountsView.setOnSearch(Consumer<String>)` matches presenter (`this::search`) and ViewFx; `SidebarFx.setActive` keys (`home`/`accounts`/`open`) match `App` calls.
- **No customer regressions:** the customer `show*` methods, `Navigator`, and every customer view/presenter/test are unchanged; only admin navigation switched from `setRoot` to `setContent`. `AdminLoginPresenter` still calls `showAdminMenu()` (now builds the dashboard) — no admin-login change.
- **Deleted `AdminMenu*`:** removing them drops 2 tests; their function (routing/logout) moves to the App sidebar wiring (compile-verified + manual), consistent with how the rest of `App` is validated.

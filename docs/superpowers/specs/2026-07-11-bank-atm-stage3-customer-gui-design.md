# Stage 3 — Customer ATM GUI (JavaFX, MVP)

**Project:** Bank Management System & ATM Simulator
**Stage:** 3 of 4 (Customer-facing ATM GUI)
**Date:** 2026-07-11
**Status:** Approved design

---

## 1. Context & Scope

Stages 1–2 delivered the data foundation and the service layer (`AuthService`,
`AccountService`) with all business rules, atomic transactions, and BCrypt
hashing, tested against MySQL `bank_test`. Stage 3 adds the **Customer ATM
desktop GUI** in JavaFX. It is a thin presentation layer over the existing
services — it adds NO new business rules.

Series recap: **1. DB foundation (done) → 2. Service layer (done) →
3. Customer ATM GUI (this spec) → 4. Admin GUI.**

### In scope
- A JavaFX desktop app the customer runs (`mvn javafx:run`).
- Screens: Login, Open Account (self-service), Menu, Balance, Deposit,
  Withdraw, Transfer, Mini-statement, Change PIN, plus Logout.
- MVP structure: thin JavaFX **views** behind view interfaces, plain-Java
  **presenters** holding all logic, a **Navigator** for screen switching, and a
  **Session** holding the logged-in account.
- Friendly mapping of `BankServiceException` subtypes to on-screen messages.
- Headless **presenter unit tests** against the real services on `bank_test`.

### Out of scope (deferred)
- Admin GUI (Stage 4).
- Automated JavaFX-widget UI tests (TestFX). The view layer is verified by
  running the app; logic lives in tested presenters.
- Any new business rules, persistence changes, or service changes. If a small
  gap is found, it is fixed in the service layer, not the GUI.
- Theming/skinning beyond a clean, readable default layout.

### Environment
Java 17, Maven, MySQL 9.7 (`bank`/`bank_test`), JUnit 5. Adds JavaFX 21.

---

## 2. Architecture — Model-View-Presenter

The point of MVP here is that all logic is testable without starting the JavaFX
toolkit. Views are dumb; presenters hold the logic and are unit-tested against
fake views.

```
   JavaFX view (thin)                Presenter (plain Java)         Services (Stage 2)
   implements <ScreenView>  <——uses——  <Screen>Presenter  ——calls——> AuthService / AccountService
        |                                   |  uses
        | (App wires them)                  +——> Navigator (interface), Session
        v
   App (JavaFX Application): builds views+presenters, switches scenes
```

- **View interface** (`<Screen>View`): getters for inputs, `showError(String)`,
  `showMessage(String)`, and a way to register the action (e.g.
  `setSubmitAction(Runnable)`). No logic.
- **JavaFX view** (`<Screen>ViewFx`): builds controls programmatically,
  implements the view interface. No logic, no service calls.
- **Presenter** (`<Screen>Presenter`): constructed with its view interface, the
  needed service(s), a `Navigator`, and (where relevant) the `Session`. Reads
  inputs, calls services, catches typed exceptions → `view.showError(...)`, and
  navigates on success. **Imports no JavaFX.**
- **`Navigator`** (interface): `showLogin()`, `showOpenAccount()`, `showMenu()`,
  `showBalance()`, `showDeposit()`, `showWithdraw()`, `showTransfer()`,
  `showMiniStatement()`, `showChangePin()`. Presenters depend on this interface,
  not on JavaFX, so they stay testable. The real implementation lives in `App`.
- **`Session`**: holds the current logged-in account number (`long`), set on
  login, cleared on logout; `requireAccount()` returns it or throws if absent.

### View style
Views are built **programmatically in plain Java** (no FXML). Views are dumb, so
FXML's controller wiring adds ceremony without benefit.

### Package layout (new — `com.bank.ui`)
```
com.bank.ui
├── App.java                 (JavaFX Application; builds views+presenters; real Navigator)
├── Navigator.java           (interface)
├── Session.java
├── Messages.java            (maps BankServiceException subtypes -> user text)
├── view/
│   ├── LoginView.java  ... (interface per screen)
│   ├── LoginViewFx.java ... (JavaFX impl per screen)
│   └── (OpenAccount, Menu, Balance, Deposit, Withdraw, Transfer, MiniStatement, ChangePin)
└── presenter/
    ├── LoginPresenter.java ... (one per screen)
    └── (OpenAccount, Balance, Deposit, Withdraw, Transfer, MiniStatement, ChangePin)
```
(Menu is navigation-only; it may need just a view + wiring, no presenter logic
beyond routing button clicks to `Navigator`.)

---

## 3. Screens & Flow

```
Login ──"Open account"──> OpenAccount ──success──> Login
Login ──authenticate ok──> Menu
Menu ──> { Balance, Deposit, Withdraw, Transfer, MiniStatement, ChangePin } ──back──> Menu
Menu ──Logout──> Login (Session cleared)
```

Per-screen behavior (all logic in the presenter):

| Screen | Inputs | Presenter action | On success | On error |
|--------|--------|------------------|------------|----------|
| **Login** | account #, PIN | `authService.authenticateCustomer` | set `Session`, `navigator.showMenu()` | `showError` (invalid / blocked) |
| **Open Account** | name, type, PIN, opening balance | `accountService.openAccount` | show new account number, back to Login | `showError` (bad PIN/amount) |
| **Balance** | — | `accountService.getBalance(session)` | show balance | `showError` |
| **Deposit** | amount | `accountService.deposit` | show new balance/message | `showError` |
| **Withdraw** | amount | `accountService.withdraw` | show new balance/message | `showError` (insufficient) |
| **Transfer** | target acct #, amount | `accountService.transfer` | show success | `showError` |
| **Mini-statement** | (count fixed, e.g. 10) | `accountService.miniStatement` | show list | `showError` |
| **Change PIN** | old PIN, new PIN | `accountService.changePin` | show success, back to Menu | `showError` (wrong old / bad new) |

Inputs are read as strings from the view; the presenter parses amounts to
`BigDecimal` and account numbers to `long`, showing a friendly error on malformed
input (before calling the service).

---

## 4. Error Handling — `Messages`

A small `Messages` helper maps exceptions to user-facing text so presenters stay
uniform:

| Exception | Message |
|-----------|---------|
| `AuthenticationException` | "Invalid account number or PIN." |
| `AccountNotActiveException` | "This account is blocked or closed." |
| `InsufficientFundsException` | "Insufficient funds." |
| `InvalidAmountException` | "Enter a valid amount greater than zero." |
| `InvalidPinException` | "PIN must be exactly 4 digits." |
| `AccountNotFoundException` | "Account not found." |
| `DaoException` / other | "Something went wrong. Please try again." |

Presenters also guard malformed numeric input (non-numeric amount / account #)
with "Enter a valid number." before touching the service.

---

## 5. Build & Run

`pom.xml` additions:
- `org.openjfx:javafx-controls:21` (and `javafx-graphics`/`javafx-base` pulled
  transitively).
- `org.openjfx:javafx-maven-plugin` configured with `mainClass`
  `com.bank.ui.App`, so the app runs via `mvn javafx:run`.

Presenter tests need **no** JavaFX (views are faked), so `mvn test` stays
headless and fast. The JavaFX toolkit is only started when the real app runs.

---

## 6. Testing

- **Presenter unit tests** (headless, real services on `bank_test`): each
  presenter is tested with a hand-written **fake view** (records `showError`/
  `showMessage`, returns canned inputs) and a **fake navigator** (records where
  it was asked to go). Assertions cover:
  - Login: valid credentials → navigator.showMenu + session set; invalid → error
    shown, no navigation; blocked account → "blocked or closed".
  - Open Account: valid → account created (verify via service) and returns to
    login; bad PIN/amount → error, no account created.
  - Deposit/Withdraw/Transfer: success changes balance (verify via service);
    insufficient/invalid → correct error, balance unchanged; malformed input →
    "valid number" error without a service call.
  - Change PIN: wrong old PIN → error; valid → succeeds (new PIN authenticates).
  - Mini-statement: returns the account's recent transactions.
- **Fakes** live under `src/test/java/com/bank/ui/` and implement the same view/
  navigator interfaces the real app uses.
- Tests seed/clean `bank_test` (FK-safe: transactions → accounts → admins) via a
  `UnitOfWork`, exactly as the Stage 2 service tests do.
- **View classes (`*ViewFx`) and `App` are not unit-tested** — they contain only
  widget wiring. They are verified by running `mvn javafx:run` and exercising the
  flows manually.

---

## 7. Success Criteria (Definition of Done for Stage 3)

1. `mvn test` green (all prior tests plus new presenter tests) headless on
   `bank_test`.
2. `mvn javafx:run` launches the ATM; a customer can open an account, log in,
   check balance, deposit, withdraw, transfer, view a mini-statement, change
   PIN, and log out — each backed by the real services.
3. Every presenter maps domain exceptions to the friendly messages in §4 and
   guards malformed numeric input.
4. No business logic lives in views or presenters that belongs in the service
   layer; presenters only orchestrate services + view + navigation.
5. Views contain no service calls and no business logic (MVP boundary intact).
6. No plaintext PIN is displayed or logged; PIN fields use masked input.

# Bank Management System & ATM Simulator

Java + MySQL + JavaFX banking / ATM simulator, built in stages.
**Stage 4 (current):** Admin GUI (JavaFX, MVP) — role-select landing, admin login, list all accounts, open account, block/close, view an account's history.
Stage 3 (done): Customer ATM GUI. Stage 2 (done): service layer. Stage 1 (done): database foundation.

## Prerequisites
- Java 17, Maven 3.9+, MySQL 9.x running on localhost:3306

## One-time database setup
```sql
CREATE DATABASE IF NOT EXISTS bank;
CREATE DATABASE IF NOT EXISTS bank_test;
```
Then set your MySQL credentials in:
- `src/main/resources/db.properties` (app)
- `src/test/resources/db.test.properties` (tests)

Tables are created automatically by `SchemaInitializer` (runs `schema.sql`).

## Run the app
```bash
mvn javafx:run
```
Launches to a **role select** screen. Choose **Customer** for the ATM, or **Admin** and log in with the seeded default **admin / admin123** (auto-created on first run; change it in production). Requires the `bank` database and valid credentials in `src/main/resources/db.properties`; tables and the default admin are created automatically.

## Build & test
```bash
mvn compile     # build
mvn test        # run the full test suite (model, DB, DAO, security, service, UI presenters)
mvn javafx:run  # launch the app (headless tests do not start JavaFX)
```

## Layout
- `com.bank.model`    — Account, Transaction, Admin, enums (data carriers)
- `com.bank.db`       — Database (config/connection), SchemaInitializer, UnitOfWork (transactions)
- `com.bank.dao`      — AccountDao / TransactionDao / AdminDao (connection-aware JDBC)
- `com.bank.security` — PasswordHasher (BCrypt)
- `com.bank.service`  — AccountService, AuthService, AdminService; AccountService.listAllAccounts / getAccount / accountHistory
- `com.bank.ui`       — App now implements Navigator + AdminNavigator; AdminSession
- `com.bank.ui.view`  — admin views (RoleSelect, AdminLogin, AdminMenu, AllAccounts, AdminOpenAccount, ManageAccount)
- `com.bank.ui.presenter`— admin presenters (tested headless)

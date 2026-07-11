# Bank Management System & ATM Simulator

Java + MySQL + JavaFX banking / ATM simulator, built in stages.
**Stage 3 (current):** customer ATM GUI — JavaFX desktop app with Model-View-Presenter architecture, 9 screens (login, account opening, menu, balance, deposit/withdraw/transfer, mini-statement, PIN change).
Stage 2 (done): service layer — transactional business logic, authentication, BCrypt PIN/password hashing.
Stage 1 (done): database foundation — schema, connection, model, DAO layer.

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

## Build & test
```bash
mvn compile     # build
mvn test        # run the full test suite (model, DB, DAO, security, service, UI presenters)
mvn javafx:run  # launch the ATM GUI (headless tests do not start JavaFX)
```

## Layout
- `com.bank.model`    — Account, Transaction, Admin, enums (data carriers)
- `com.bank.db`       — Database (config/connection), SchemaInitializer, UnitOfWork (transactions)
- `com.bank.dao`      — AccountDao / TransactionDao / AdminDao (connection-aware JDBC)
- `com.bank.security` — PasswordHasher (BCrypt)
- `com.bank.service`  — AccountService, AuthService, BankServiceException hierarchy
- `com.bank.ui`       — Session, Navigator, Messages; App (JavaFX Application + real Navigator)
- `com.bank.ui.view`  — 9 *View interfaces + *ViewFx implementations (dumb, no logic)
- `com.bank.ui.presenter` — 9 *Presenter classes (MVP logic, unit-tested headless against bank_test)

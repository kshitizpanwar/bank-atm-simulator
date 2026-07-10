# Stage 1 — Database Foundation

**Project:** Bank Management System & ATM Simulator
**Stage:** 1 of 4 (Database foundation + model + DAO layer)
**Date:** 2026-07-10
**Status:** Approved design

---

## 1. Context & Scope

This is the first spec in a multi-stage project series building a JavaFX + MySQL
banking / ATM simulator with two roles (Customer/ATM and Admin).

The overall series (Approach A — layered architecture, built in stages):

1. **Stage 1 — DB foundation + model + DAO layer** *(this spec)*
2. Stage 2 — Service layer (business rules, auth, PIN hashing, transaction logic)
3. Stage 3 — Customer ATM JavaFX GUI
4. Stage 4 — Admin JavaFX GUI

**This spec covers Stage 1 only:** the database schema, connection/config
handling, model POJOs, and a tested JDBC DAO layer. No business rules and no GUI.

### Environment (verified installed)
- Java 17 (Temurin `17.0.19`)
- Maven `3.9.16`
- MySQL Community Server `9.7`
- Target OS: Windows 11

### Out of scope for Stage 1
- Business rules (withdrawal limits, overdraft checks, transfer orchestration)
- Authentication / login flows
- PIN hashing (deferred to Stage 2 service layer)
- JavaFX / any UI
- Admin functionality beyond the table existing

---

## 2. Architecture

Clean layered structure. Stage 1 delivers the bottom two layers plus models.

```
model  (Account, Transaction, enums)      <- data carriers, no logic
  ^
dao    (interfaces + JDBC implementations) <- data access only
  ^
db     (Database connection, config, schema init)
```

Later stages add `service` (on top of `dao`) and `ui` (on top of `service`).

### Package layout
```
D:\Bank
├── pom.xml
├── schema.sql                       (optional top-level copy; canonical is in resources)
├── docs/superpowers/specs/...
└── src
    ├── main
    │   ├── java/com/bank
    │   │   ├── model
    │   │   │   ├── Account.java
    │   │   │   ├── Transaction.java
    │   │   │   ├── AccountType.java     (enum: SAVINGS, CURRENT)
    │   │   │   ├── AccountStatus.java   (enum: ACTIVE, BLOCKED, CLOSED)
    │   │   │   └── TransactionType.java (enum: DEPOSIT, WITHDRAW, TRANSFER)
    │   │   ├── dao
    │   │   │   ├── AccountDao.java          (interface)
    │   │   │   ├── TransactionDao.java      (interface)
    │   │   │   ├── AccountDaoJdbc.java       (implementation)
    │   │   │   ├── TransactionDaoJdbc.java   (implementation)
    │   │   │   └── DaoException.java         (unchecked wrapper for SQLException)
    │   │   └── db
    │   │       ├── Database.java             (reads config, hands out Connections)
    │   │       └── SchemaInitializer.java    (runs schema.sql)
    │   └── resources
    │       ├── db.properties
    │       └── schema.sql
    └── test/java/com/bank/dao
        ├── AccountDaoJdbcTest.java
        └── TransactionDaoJdbcTest.java
```

---

## 3. Maven Project (`pom.xml`)

- Group `com.bank`, artifact `bank-atm-simulator`, Java 17.
- Dependencies (Stage 1 only):
  - `mysql:mysql-connector-j` (MySQL Connector/J, current 9.x)
  - `org.junit.jupiter:junit-jupiter` (JUnit 5) — test scope
- `maven-surefire-plugin` configured to run JUnit 5 tests.
- JavaFX dependencies are **NOT** added in Stage 1 (arrive in Stage 3).

---

## 4. Database Schema (`schema.sql`)

Target database name: `bank` (and `bank_test` for tests — identical schema).
All three tables are created now so later stages need no migrations.

### `accounts`
| Column          | Type            | Notes                                   |
|-----------------|-----------------|-----------------------------------------|
| account_number  | BIGINT          | PK, 10-digit account number             |
| holder_name     | VARCHAR(100)    | NOT NULL                                |
| pin             | VARCHAR(64)     | NOT NULL (plain string in Stage 1; hashing added Stage 2 — column already sized for a hash) |
| balance         | DECIMAL(15,2)   | NOT NULL DEFAULT 0.00                    |
| account_type    | VARCHAR(20)     | NOT NULL (maps to AccountType enum)     |
| status          | VARCHAR(20)     | NOT NULL DEFAULT 'ACTIVE'               |
| created_at      | TIMESTAMP       | NOT NULL DEFAULT CURRENT_TIMESTAMP      |

### `transactions`
| Column          | Type            | Notes                                   |
|-----------------|-----------------|-----------------------------------------|
| id              | BIGINT          | PK AUTO_INCREMENT                       |
| account_number  | BIGINT          | NOT NULL, FK -> accounts(account_number)|
| type            | VARCHAR(20)     | NOT NULL (maps to TransactionType enum) |
| amount          | DECIMAL(15,2)   | NOT NULL                                |
| balance_after   | DECIMAL(15,2)   | NOT NULL (running balance snapshot)     |
| timestamp       | TIMESTAMP       | NOT NULL DEFAULT CURRENT_TIMESTAMP      |

### `admins`
| Column          | Type            | Notes                                   |
|-----------------|-----------------|-----------------------------------------|
| id              | INT             | PK AUTO_INCREMENT                       |
| username        | VARCHAR(50)     | NOT NULL UNIQUE                         |
| password        | VARCHAR(64)     | NOT NULL (used from Stage 4)            |

**Money:** always `DECIMAL(15,2)` in SQL and `java.math.BigDecimal` in Java —
never `double`/`float`.

---

## 5. Connection & Config (`db` package)

### `db.properties` (in `src/main/resources`)
```
db.url=jdbc:mysql://localhost:3306/bank
db.user=root
db.password=
```
Credentials live here, never hardcoded in Java. Tests point at `bank_test`.

### `Database.java`
- Loads `db.properties` from the classpath.
- Exposes `Connection getConnection()` that returns a fresh JDBC connection
  (Connector/J via `DriverManager`). Simple per-operation connections — no
  pooling in Stage 1 (YAGNI; can add later).
- Allows the config source to be overridden so tests can target `bank_test`
  (e.g. a constructor/factory taking a properties path or a `Properties` object).

### `SchemaInitializer.java`
- Runs `schema.sql` against a given connection/database to create tables if they
  don't exist (idempotent — `CREATE TABLE IF NOT EXISTS`).
- Used both for first-time setup and by tests to prepare `bank_test`.

---

## 6. Model Classes (`model`)

Plain POJOs — data carriers only, no persistence or business logic.

- **`Account`**: `long accountNumber`, `String holderName`, `String pin`,
  `BigDecimal balance`, `AccountType accountType`, `AccountStatus status`,
  `LocalDateTime createdAt`. Constructors, getters, `equals`/`hashCode` on
  `accountNumber`, `toString`.
- **`Transaction`**: `long id`, `long accountNumber`, `TransactionType type`,
  `BigDecimal amount`, `BigDecimal balanceAfter`, `LocalDateTime timestamp`.
- **Enums**: `AccountType {SAVINGS, CURRENT}`, `AccountStatus {ACTIVE, BLOCKED,
  CLOSED}`, `TransactionType {DEPOSIT, WITHDRAW, TRANSFER}`.

Enums are stored in the DB as their `name()` string and parsed back via
`valueOf()` in the DAO.

---

## 7. DAO Layer (`dao`)

Interfaces plus JDBC implementations. **All queries use `PreparedStatement`**
(parameterized — no string concatenation, safe from SQL injection). DAOs do data
access only; no validation of business rules.

`SQLException` is wrapped in an unchecked `DaoException` so callers aren't forced
to handle checked exceptions everywhere.

### `AccountDao`
```java
void create(Account account);
Optional<Account> findByAccountNumber(long accountNumber);
List<Account> findAll();
void updateBalance(long accountNumber, BigDecimal newBalance);
void updatePin(long accountNumber, String newPin);
void updateStatus(long accountNumber, AccountStatus status);
```

### `TransactionDao`
```java
void insert(Transaction transaction);      // sets generated id back on the object
List<Transaction> findByAccountNumber(long accountNumber);
List<Transaction> findRecent(long accountNumber, int limit);  // for mini-statement
```

Notes:
- `updateBalance` writes exactly the value given — the *decision* about the new
  balance is a Stage 2 service concern, not the DAO's.
- `findRecent` orders by `timestamp DESC` / `id DESC` and applies `LIMIT`.

---

## 8. Testing (JUnit 5, TDD)

- Tests run against a **real separate MySQL database `bank_test`** (not the
  production `bank`, not H2). Same schema, real JDBC/SQL — no behavioral
  surprises in later stages.
- `SchemaInitializer` creates the schema in `bank_test`; each test cleans up its
  rows (e.g. `@BeforeEach`/`@AfterEach` truncating tables) so tests are
  independent and repeatable.
- **Test-driven:** DAO tests are written first (red), then the JDBC
  implementations are written to make them pass (green).

### Coverage
- `AccountDaoJdbcTest`: create + read-back round-trip; `findByAccountNumber`
  present/absent (`Optional`); `findAll`; `updateBalance`; `updatePin`;
  `updateStatus`; `BigDecimal` scale preserved (e.g. `100.00`).
- `TransactionDaoJdbcTest`: insert sets generated id; `findByAccountNumber`
  returns inserted rows; `findRecent` respects ordering and `limit`.

A short README note documents the one-time prerequisite: create the `bank` and
`bank_test` databases (empty) and set credentials in `db.properties` — the
`SchemaInitializer` creates the tables.

---

## 9. Success Criteria (Definition of Done for Stage 1)

1. `mvn test` runs green against a local MySQL `bank_test` database.
2. `schema.sql` creates all three tables cleanly on an empty database.
3. `Database` reads credentials from `db.properties` (nothing hardcoded).
4. All DAO methods listed in §7 are implemented with `PreparedStatement` and
   covered by passing tests.
5. Money is represented as `BigDecimal` / `DECIMAL(15,2)` end to end.
6. No business logic or UI code has leaked into the `model`/`dao`/`db` layers.

# Stage 2 — Service Layer

**Project:** Bank Management System & ATM Simulator
**Stage:** 2 of 4 (Business rules, authentication, PIN/password hashing, transactional operations)
**Date:** 2026-07-10
**Status:** Approved design

---

## 1. Context & Scope

Stage 1 delivered the data foundation: model POJOs, `Database`/`SchemaInitializer`,
and a JDBC DAO layer (`AccountDao`, `TransactionDao`) tested against a real MySQL
`bank_test` database. Stage 2 adds the **service layer** — the only layer the
Stage 3/4 JavaFX GUIs will call. It orchestrates DAOs inside database
transactions and enforces all business rules.

Series recap: **1. DB foundation (done) → 2. Service layer (this spec) →
3. Customer ATM GUI → 4. Admin GUI.**

### In scope
- Money operations: `deposit`, `withdraw` (overdraft-prevented), `getBalance`,
  `transfer` (atomic debit+credit), `miniStatement`, `changePin`.
- Authentication: customer (account # + PIN), admin (username + password).
- Account lifecycle: `openAccount` (generate account #, hash PIN),
  `blockAccount`, `closeAccount`.
- Password/PIN hashing with BCrypt (jBCrypt).
- Atomicity: every money operation writes the balance update **and** the
  transaction row in a single DB transaction (all-or-nothing).
- A domain exception hierarchy the GUIs can catch.

### Out of scope (deferred)
- Any JavaFX / UI (Stages 3–4).
- **Minimum-balance rule** — overdraft prevention only.
- **Failed-PIN lockout** — needs a `failed_attempts` schema column (migration);
  noted as a future enhancement.
- Interest, statements export, multi-currency, etc.

### Environment (unchanged from Stage 1)
Java 17, Maven, MySQL 9.7 (`bank` / `bank_test`), JUnit 5. jBCrypt added.

---

## 2. Architecture

New/changed pieces layered on Stage 1:

```
service  (AccountService, AuthService, exceptions)   <- business rules, orchestration
   |  uses
security (PasswordHasher)                            <- BCrypt hash/verify
   |
dao      (AccountDao, TransactionDao, AdminDao — all Connection-aware)
   |  runs inside
db       (UnitOfWork over Database)                  <- one transactional Connection
```

Key change: **DAOs no longer open their own connections.** A `UnitOfWork`
supplies one `Connection` (with a transaction boundary) that the service passes
to each DAO call, so multi-step operations commit or roll back as a unit.

### Package layout (additions/changes)
```
com.bank
├── db
│   ├── Database.java            (unchanged)
│   ├── SchemaInitializer.java   (unchanged)
│   └── UnitOfWork.java          (NEW)
├── dao
│   ├── AccountDao.java          (CHANGED: methods take Connection)
│   ├── AccountDaoJdbc.java      (CHANGED: uses supplied Connection)
│   ├── TransactionDao.java      (CHANGED: methods take Connection)
│   ├── TransactionDaoJdbc.java  (CHANGED: uses supplied Connection)
│   ├── AdminDao.java            (NEW)
│   ├── AdminDaoJdbc.java        (NEW)
│   └── DaoException.java        (unchanged)
├── model
│   └── Admin.java               (NEW: id, username, passwordHash)
├── security
│   └── PasswordHasher.java      (NEW: BCrypt wrapper)
└── service
    ├── AccountService.java      (NEW)
    ├── AuthService.java         (NEW)
    ├── BankServiceException.java        (NEW, base — unchecked)
    ├── InsufficientFundsException.java  (NEW)
    ├── AccountNotActiveException.java   (NEW)
    ├── AccountNotFoundException.java     (NEW)
    ├── InvalidAmountException.java       (NEW)
    ├── InvalidPinException.java          (NEW)
    └── AuthenticationException.java      (NEW)
```

---

## 3. Infrastructure — `UnitOfWork` and Connection-aware DAOs

### `UnitOfWork` (`com.bank.db`)
Runs a block of work inside one transactional connection:

```
<T> T execute(Function<Connection, T> work)
```
- Opens a `Connection` from `Database`, `setAutoCommit(false)`.
- Runs `work`; on normal return `commit()` and return the value.
- On any exception `rollback()` and rethrow (wrapped in `DaoException` if it is a
  raw `SQLException`; domain exceptions from `work` propagate as-is after rollback).
- Always closes the connection (try-with-resources / finally).

Reads and writes both run through `UnitOfWork` for a single uniform DB access
style. A read that commits is harmless.

### DAO refactor
Every DAO method takes a `Connection` as its **first** parameter, and the JDBC
implementations use it directly (no `Database.getConnection()` inside). The DAO
implementations become effectively stateless (no `Database` field required).

New signatures:

```java
// AccountDao
void create(Connection c, Account account);
Optional<Account> findByAccountNumber(Connection c, long accountNumber);
List<Account> findAll(Connection c);
void updateBalance(Connection c, long accountNumber, BigDecimal newBalance);
void updatePin(Connection c, long accountNumber, String newPinHash);
void updateStatus(Connection c, long accountNumber, AccountStatus status);

// TransactionDao
void insert(Connection c, Transaction transaction);
List<Transaction> findByAccountNumber(Connection c, long accountNumber);
List<Transaction> findRecent(Connection c, long accountNumber, int limit);

// AdminDao (new)
Optional<Admin> findByUsername(Connection c, String username);
void create(Connection c, Admin admin);
```

**Stage 1 DAO tests are updated** to the new signatures — they obtain a
`Connection` (via `UnitOfWork` or `Database.getConnection()`) and pass it in.
This is the deliberate, approved cost of correct atomicity.

---

## 4. Model & DAO additions

- **`Admin`** (`com.bank.model`): immutable POJO with `long id`,
  `String username`, `String passwordHash`, constructor + getters.
- **`AdminDao` / `AdminDaoJdbc`**: `findByUsername`, `create`, using
  `PreparedStatement`, storing/reading against the existing `admins` table
  (`id`, `username`, `password`). The `password` column stores the BCrypt hash.

---

## 5. Security — `PasswordHasher` (`com.bank.security`)

Thin wrapper over jBCrypt (`org.mindrot:jbcrypt`, version `0.4`):

```java
String hash(String raw);              // BCrypt.hashpw(raw, BCrypt.gensalt())
boolean verify(String raw, String hash); // BCrypt.checkpw(raw, hash)
```

Used for both customer PINs and admin passwords. BCrypt output is 60 chars and
fits the existing `VARCHAR(64)` `pin` / `password` columns — **no schema change.**

---

## 6. Services

### `AccountService`
Constructed with `UnitOfWork`, `AccountDao`, `TransactionDao`, `PasswordHasher`.
Every money operation runs inside a single `UnitOfWork.execute(...)` and records
both the balance change and a `Transaction` row (with correct `balanceAfter`).

| Method | Behavior & rules |
|--------|------------------|
| `Account openAccount(String holderName, String rawPin, AccountType type, BigDecimal openingBalance)` | Validate PIN is exactly 4 digits (`InvalidPinException`) and openingBalance ≥ 0 (`InvalidAmountException`); generate a unique 10-digit account number (retry on collision via `findByAccountNumber`); store BCrypt-hashed PIN; status `ACTIVE`; persist; return the new `Account`. If openingBalance > 0, record a `DEPOSIT` transaction. |
| `BigDecimal getBalance(long acct)` | Load account (or `AccountNotFoundException`); return balance. |
| `void deposit(long acct, BigDecimal amount)` | amount > 0 (`InvalidAmountException`); account `ACTIVE` (`AccountNotActiveException`); balance += amount; insert `DEPOSIT` row. Atomic. |
| `void withdraw(long acct, BigDecimal amount)` | amount > 0; account `ACTIVE`; `balance ≥ amount` else `InsufficientFundsException`; balance -= amount; insert `WITHDRAW` row. Atomic. |
| `void transfer(long from, long to, BigDecimal amount)` | amount > 0; `from` ≠ `to`; both accounts exist and `ACTIVE`; `from` balance ≥ amount; debit `from`, credit `to`, insert a `TRANSFER` row for each with its own `balanceAfter`. All in one transaction — any failure rolls back both. |
| `List<Transaction> miniStatement(long acct, int n)` | account exists; return `findRecent(acct, n)`. |
| `void changePin(long acct, String oldPin, String newPin)` | account exists; verify `oldPin` against stored hash (`AuthenticationException` on mismatch); validate `newPin` is exactly 4 digits (`InvalidPinException`); store new BCrypt hash. |
| `void blockAccount(long acct)` / `void closeAccount(long acct)` | account exists; set status `BLOCKED` / `CLOSED`. |

Amount handling uses `BigDecimal.compareTo` (never `equals`) and amounts are
treated at scale 2.

### `AuthService`
Constructed with `UnitOfWork`, `AccountDao`, `AdminDao`, `PasswordHasher`.

| Method | Behavior |
|--------|----------|
| `Account authenticateCustomer(long acct, String rawPin)` | Load account; if absent or PIN mismatch → `AuthenticationException` (same message, don't leak which failed); if status ≠ `ACTIVE` → `AccountNotActiveException`; else return the `Account`. |
| `Admin authenticateAdmin(String username, String rawPassword)` | Load admin by username; absent or password mismatch → `AuthenticationException`; else return the `Admin`. |

---

## 7. Error Handling

Unchecked hierarchy in `com.bank.service`:

```
BankServiceException (extends RuntimeException)
├── AccountNotFoundException
├── AccountNotActiveException
├── InsufficientFundsException
├── InvalidAmountException      (non-positive / negative amounts)
├── InvalidPinException         (PIN not exactly 4 digits)
└── AuthenticationException
```

Services throw these for expected domain failures; `DaoException` (from Stage 1)
still represents unexpected data-access failures. GUIs catch
`BankServiceException` subtypes to present friendly messages.

---

## 8. Testing (TDD, real `bank_test`)

- **`PasswordHasherTest`** (pure unit): `hash` output ≠ raw and starts with a
  BCrypt prefix; `verify` returns true for the right raw and false for a wrong
  one; two hashes of the same input differ (salt).
- **`UnitOfWorkTest`** (integration): a committed block persists; a block that
  throws mid-way leaves **no** partial write (rollback proven by reading back).
- **`AccountServiceTest`** (integration): happy paths — open → deposit →
  withdraw → transfer → mini-statement → changePin; failure paths — deposit/
  withdraw of non-positive amount rejected, withdraw over balance →
  `InsufficientFundsException`, operation on `BLOCKED`/`CLOSED` account rejected,
  transfer with insufficient funds rolls back leaving **both** balances
  unchanged, `changePin` with wrong old PIN → `AuthenticationException`.
- **`AuthServiceTest`** (integration): correct customer PIN returns the account;
  wrong PIN / unknown account → `AuthenticationException`; blocked account →
  `AccountNotActiveException`; admin correct/incorrect password.
- **Stage 1 DAO tests** (`AccountDaoJdbcTest`, `TransactionDaoJdbcTest`,
  `SchemaInitializerTest` as needed) updated to the connection-aware signatures.

Tests clean their tables between runs (FK-safe order: transactions → accounts;
admins as needed) and connect via `db.test.properties` → `bank_test`.

---

## 9. Dependencies

Add to `pom.xml`:
- `org.mindrot:jbcrypt:0.4` (compile scope).

---

## 10. Success Criteria (Definition of Done for Stage 2)

1. `mvn test` green against `bank_test` — new service/security/UnitOfWork tests
   plus the updated Stage 1 DAO tests all pass.
2. All money operations are atomic: a forced mid-operation failure leaves
   balances and transaction rows consistent (proven by a rollback test).
3. PINs and admin passwords are stored only as BCrypt hashes; no plaintext
   secret is ever written to the DB by the service layer.
4. All business rules from §6 are enforced and covered by tests, including the
   failure paths.
5. Domain failures surface as `BankServiceException` subtypes; unexpected
   data-access failures as `DaoException`.
6. No UI code and no business logic leaks below the service layer (DAOs remain
   pure data access).

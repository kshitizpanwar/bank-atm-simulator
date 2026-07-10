# Stage 2 — Service Layer Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the service layer — transactional business logic, authentication, and BCrypt PIN/password hashing — on top of the Stage 1 DAO layer.

**Architecture:** A `UnitOfWork` supplies one transactional JDBC `Connection`; DAOs are refactored to accept that `Connection` (no longer opening their own), so multi-step money operations commit/roll back atomically. `AccountService` and `AuthService` orchestrate DAOs inside a `UnitOfWork` and enforce all rules. `PasswordHasher` wraps jBCrypt.

**Tech Stack:** Java 17, Maven, MySQL 9.x (JDBC), JUnit 5, jBCrypt.

## Global Constraints

- Java 17; Maven; MySQL 9.7 (`bank` / `bank_test`). Add dependency `org.mindrot:jbcrypt:0.4` (compile scope).
- Money is `java.math.BigDecimal` (scale 2, `RoundingMode.HALF_UP`), compared with `compareTo` — never `double`/`float`, never `equals`.
- All SQL through `PreparedStatement`.
- **DAO methods take a `Connection` as the first parameter and must NOT open or close it** — the `UnitOfWork` owns the connection lifecycle. DAOs close only their own `PreparedStatement`/`ResultSet`. DAOs stay pure data access (no business rules).
- PINs and admin passwords are stored ONLY as BCrypt hashes (60 chars, fits `VARCHAR(64)`); the service layer never writes a plaintext secret.
- Expected domain failures throw `BankServiceException` subtypes; unexpected data-access failures throw `DaoException` (Stage 1).
- Tests run against real MySQL `bank_test`; clean tables in FK-safe order (`transactions` before `accounts`; `admins` as needed) via `db.test.properties`.
- Package root `com.bank`.

## Prerequisite (already satisfied)

`bank` and `bank_test` databases exist; `src/test/resources/db.test.properties` has working credentials (skip-worktree, not committed). No schema change is needed in Stage 2.

---

## File Structure

- Modify: `pom.xml` — add jBCrypt.
- Create: `src/main/java/com/bank/security/PasswordHasher.java`
- Create: `src/main/java/com/bank/db/UnitOfWork.java`
- Modify: `src/main/java/com/bank/dao/AccountDao.java`, `AccountDaoJdbc.java` — Connection-aware.
- Modify: `src/main/java/com/bank/dao/TransactionDao.java`, `TransactionDaoJdbc.java` — Connection-aware.
- Modify: `src/test/java/com/bank/dao/AccountDaoJdbcTest.java`, `TransactionDaoJdbcTest.java` — updated to new signatures.
- Create: `src/main/java/com/bank/model/Admin.java`
- Create: `src/main/java/com/bank/dao/AdminDao.java`, `AdminDaoJdbc.java`
- Create: `src/main/java/com/bank/service/BankServiceException.java` + 6 subtype files.
- Create: `src/main/java/com/bank/service/AccountService.java`, `AuthService.java`
- Create tests: `PasswordHasherTest`, `UnitOfWorkTest`, `AdminDaoJdbcTest`, `AccountServiceTest`, `AuthServiceTest`.
- Modify: `README.md` — Stage 2 section.

---

## Task 1: jBCrypt dependency + PasswordHasher

**Files:**
- Modify: `pom.xml`
- Create: `src/main/java/com/bank/security/PasswordHasher.java`
- Test: `src/test/java/com/bank/security/PasswordHasherTest.java`

**Interfaces:**
- Consumes: nothing.
- Produces: `PasswordHasher` with `String hash(String raw)` and `boolean verify(String raw, String hash)`.

- [ ] **Step 1: Add jBCrypt to `pom.xml`**

Add inside `<dependencies>` (alongside the existing connector-j and junit deps):

```xml
        <dependency>
            <groupId>org.mindrot</groupId>
            <artifactId>jbcrypt</artifactId>
            <version>0.4</version>
        </dependency>
```

- [ ] **Step 2: Write the failing test**

`src/test/java/com/bank/security/PasswordHasherTest.java`:

```java
package com.bank.security;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PasswordHasherTest {
    private final PasswordHasher hasher = new PasswordHasher();

    @Test
    void hashIsNotThePlaintextAndVerifies() {
        String hash = hasher.hash("1234");
        assertNotEquals("1234", hash);
        assertTrue(hash.startsWith("$2"), "expected a BCrypt hash");
        assertTrue(hasher.verify("1234", hash));
    }

    @Test
    void verifyFailsForWrongInput() {
        String hash = hasher.hash("1234");
        assertFalse(hasher.verify("9999", hash));
    }

    @Test
    void sameInputProducesDifferentHashes() {
        assertNotEquals(hasher.hash("1234"), hasher.hash("1234"));
    }
}
```

- [ ] **Step 3: Run test to verify it fails**

Run: `mvn -q -Dtest=PasswordHasherTest test`
Expected: FAIL — cannot find symbol `PasswordHasher`.

- [ ] **Step 4: Implement `PasswordHasher`**

`src/main/java/com/bank/security/PasswordHasher.java`:

```java
package com.bank.security;

import org.mindrot.jbcrypt.BCrypt;

public class PasswordHasher {

    public String hash(String raw) {
        return BCrypt.hashpw(raw, BCrypt.gensalt());
    }

    public boolean verify(String raw, String hash) {
        return BCrypt.checkpw(raw, hash);
    }
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `mvn -q -Dtest=PasswordHasherTest test`
Expected: PASS (3 tests).

- [ ] **Step 6: Commit**

```bash
git add pom.xml src/main/java/com/bank/security/PasswordHasher.java src/test/java/com/bank/security/PasswordHasherTest.java
git commit -m "feat(security): add BCrypt PasswordHasher"
```

---

## Task 2: UnitOfWork

**Files:**
- Create: `src/main/java/com/bank/db/UnitOfWork.java`
- Test: `src/test/java/com/bank/db/UnitOfWorkTest.java`

**Interfaces:**
- Consumes: `Database`, `DaoException`, `SchemaInitializer` (Stage 1).
- Produces: `UnitOfWork(Database db)` with `<T> T execute(Function<Connection,T> work)` and `void executeVoid(Consumer<Connection> work)`. Commits on normal return; rolls back and rethrows on exception; always closes the connection. The `Connection` handed to `work` is in a transaction (`autoCommit=false`) and must NOT be closed by `work`.

- [ ] **Step 1: Write the failing test**

`src/test/java/com/bank/db/UnitOfWorkTest.java`:

```java
package com.bank.db;

import com.bank.dao.DaoException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

class UnitOfWorkTest {

    private static final Database db = Database.fromResource("db.test.properties");
    private static final UnitOfWork uow = new UnitOfWork(db);
    private static final long ACC = 5000000001L;

    @BeforeAll
    static void schema() { SchemaInitializer.initialize(db); }

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

    private void insertAccount(Connection c) {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO accounts(account_number,holder_name,pin,balance,account_type,status)"
                        + " VALUES(?,?,?,?,?,?)")) {
            ps.setLong(1, ACC);
            ps.setString(2, "Test");
            ps.setString(3, "hash");
            ps.setBigDecimal(4, new BigDecimal("10.00"));
            ps.setString(5, "SAVINGS");
            ps.setString(6, "ACTIVE");
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DaoException("insert failed", e);
        }
    }

    private boolean accountExists() {
        return uow.execute(c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT 1 FROM accounts WHERE account_number=?")) {
                ps.setLong(1, ACC);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next();
                }
            } catch (SQLException e) {
                throw new DaoException("exists failed", e);
            }
        });
    }

    @Test
    void commitsOnSuccess() {
        uow.executeVoid(this::insertAccount);
        assertTrue(accountExists());
    }

    @Test
    void rollsBackOnException() {
        assertThrows(RuntimeException.class, () ->
            uow.executeVoid(c -> {
                insertAccount(c);
                throw new RuntimeException("boom"); // force rollback after a write
            }));
        assertFalse(accountExists(), "write must be rolled back");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q -Dtest=UnitOfWorkTest test`
Expected: FAIL — cannot find symbol `UnitOfWork`.

- [ ] **Step 3: Implement `UnitOfWork`**

`src/main/java/com/bank/db/UnitOfWork.java`:

```java
package com.bank.db;

import com.bank.dao.DaoException;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.function.Consumer;
import java.util.function.Function;

public class UnitOfWork {

    private final Database database;

    public UnitOfWork(Database database) {
        this.database = database;
    }

    public <T> T execute(Function<Connection, T> work) {
        Connection c = null;
        try {
            c = database.getConnection();
            c.setAutoCommit(false);
            try {
                T result = work.apply(c);
                c.commit();
                return result;
            } catch (RuntimeException e) {
                c.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new DaoException("transaction failed", e);
        } finally {
            if (c != null) {
                try {
                    c.close();
                } catch (SQLException ignored) {
                    // closing best-effort; original outcome already decided
                }
            }
        }
    }

    public void executeVoid(Consumer<Connection> work) {
        execute(c -> {
            work.accept(c);
            return null;
        });
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -q -Dtest=UnitOfWorkTest test`
Expected: PASS (2 tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/bank/db/UnitOfWork.java src/test/java/com/bank/db/UnitOfWorkTest.java
git commit -m "feat(db): add UnitOfWork transaction runner"
```

---

## Task 3: Refactor AccountDao to be Connection-aware

**Files:**
- Modify: `src/main/java/com/bank/dao/AccountDao.java`
- Modify: `src/main/java/com/bank/dao/AccountDaoJdbc.java`
- Modify (test): `src/test/java/com/bank/dao/AccountDaoJdbcTest.java`

**Interfaces:**
- Consumes: `Database`, `UnitOfWork`, `SchemaInitializer`, `DaoException`, model + enums.
- Produces `AccountDao` (Connection-aware):
  - `void create(Connection c, Account account)`
  - `Optional<Account> findByAccountNumber(Connection c, long accountNumber)`
  - `List<Account> findAll(Connection c)`
  - `void updateBalance(Connection c, long accountNumber, BigDecimal newBalance)`
  - `void updatePin(Connection c, long accountNumber, String newPinHash)`
  - `void updateStatus(Connection c, long accountNumber, AccountStatus status)`
  - Implementation `AccountDaoJdbc()` (no-arg; stateless).

- [ ] **Step 1: Update the test to the new signatures (write it first — it will fail to compile)**

Replace the entire contents of `src/test/java/com/bank/dao/AccountDaoJdbcTest.java`:

```java
package com.bank.dao;

import com.bank.db.Database;
import com.bank.db.SchemaInitializer;
import com.bank.db.UnitOfWork;
import com.bank.model.Account;
import com.bank.model.AccountStatus;
import com.bank.model.AccountType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class AccountDaoJdbcTest {

    private static final Database db = Database.fromResource("db.test.properties");
    private static final UnitOfWork uow = new UnitOfWork(db);
    private final AccountDao dao = new AccountDaoJdbc();

    @BeforeAll
    static void createSchema() {
        SchemaInitializer.initialize(db);
    }

    @BeforeEach
    void cleanTables() {
        uow.executeVoid(c -> {
            try (Statement st = c.createStatement()) {
                st.execute("DELETE FROM transactions");
                st.execute("DELETE FROM accounts");
            } catch (SQLException e) {
                throw new DaoException("clean failed", e);
            }
        });
    }

    private Account sample(long number) {
        return new Account(number, "Asha", "1234", new BigDecimal("100.00"),
                AccountType.SAVINGS, AccountStatus.ACTIVE, LocalDateTime.now());
    }

    @Test
    void createThenFindRoundTrips() {
        uow.executeVoid(c -> dao.create(c, sample(1000000001L)));

        Optional<Account> found = uow.execute(c -> dao.findByAccountNumber(c, 1000000001L));
        assertTrue(found.isPresent());
        Account a = found.get();
        assertEquals("Asha", a.getHolderName());
        assertEquals("1234", a.getPin());
        assertEquals(0, new BigDecimal("100.00").compareTo(a.getBalance()));
        assertEquals(AccountType.SAVINGS, a.getAccountType());
        assertEquals(AccountStatus.ACTIVE, a.getStatus());
        assertNotNull(a.getCreatedAt());
    }

    @Test
    void findByAccountNumberReturnsEmptyWhenAbsent() {
        assertTrue(uow.execute(c -> dao.findByAccountNumber(c, 9999999999L)).isEmpty());
    }

    @Test
    void findAllReturnsEveryAccount() {
        uow.executeVoid(c -> dao.create(c, sample(1000000001L)));
        uow.executeVoid(c -> dao.create(c, sample(1000000002L)));
        List<Account> all = uow.execute(dao::findAll);
        assertEquals(2, all.size());
    }

    @Test
    void updateBalancePersistsExactValue() {
        uow.executeVoid(c -> dao.create(c, sample(1000000001L)));
        uow.executeVoid(c -> dao.updateBalance(c, 1000000001L, new BigDecimal("250.75")));
        BigDecimal balance = uow.execute(c ->
                dao.findByAccountNumber(c, 1000000001L).orElseThrow().getBalance());
        assertEquals(0, new BigDecimal("250.75").compareTo(balance));
    }

    @Test
    void updatePinPersists() {
        uow.executeVoid(c -> dao.create(c, sample(1000000001L)));
        uow.executeVoid(c -> dao.updatePin(c, 1000000001L, "4321"));
        assertEquals("4321", uow.execute(c ->
                dao.findByAccountNumber(c, 1000000001L).orElseThrow().getPin()));
    }

    @Test
    void updateStatusPersists() {
        uow.executeVoid(c -> dao.create(c, sample(1000000001L)));
        uow.executeVoid(c -> dao.updateStatus(c, 1000000001L, AccountStatus.BLOCKED));
        assertEquals(AccountStatus.BLOCKED, uow.execute(c ->
                dao.findByAccountNumber(c, 1000000001L).orElseThrow().getStatus()));
    }
}
```

- [ ] **Step 2: Run test to verify it fails to compile**

Run: `mvn -q -Dtest=AccountDaoJdbcTest test`
Expected: FAIL — compilation errors (old `AccountDaoJdbc(Database)` constructor and no-`Connection` method signatures no longer match).

- [ ] **Step 3: Update the `AccountDao` interface**

Replace `src/main/java/com/bank/dao/AccountDao.java`:

```java
package com.bank.dao;

import com.bank.model.Account;
import com.bank.model.AccountStatus;

import java.math.BigDecimal;
import java.sql.Connection;
import java.util.List;
import java.util.Optional;

public interface AccountDao {
    void create(Connection c, Account account);
    Optional<Account> findByAccountNumber(Connection c, long accountNumber);
    List<Account> findAll(Connection c);
    void updateBalance(Connection c, long accountNumber, BigDecimal newBalance);
    void updatePin(Connection c, long accountNumber, String newPinHash);
    void updateStatus(Connection c, long accountNumber, AccountStatus status);
}
```

- [ ] **Step 4: Update the `AccountDaoJdbc` implementation**

Replace `src/main/java/com/bank/dao/AccountDaoJdbc.java` (stateless; uses the supplied `Connection`; never opens or closes it):

```java
package com.bank.dao;

import com.bank.model.Account;
import com.bank.model.AccountStatus;
import com.bank.model.AccountType;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class AccountDaoJdbc implements AccountDao {

    @Override
    public void create(Connection c, Account account) {
        String sql = "INSERT INTO accounts "
                + "(account_number, holder_name, pin, balance, account_type, status) "
                + "VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, account.getAccountNumber());
            ps.setString(2, account.getHolderName());
            ps.setString(3, account.getPin());
            ps.setBigDecimal(4, account.getBalance());
            ps.setString(5, account.getAccountType().name());
            ps.setString(6, account.getStatus().name());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DaoException("create account failed", e);
        }
    }

    @Override
    public Optional<Account> findByAccountNumber(Connection c, long accountNumber) {
        String sql = "SELECT account_number, holder_name, pin, balance, account_type, status, created_at "
                + "FROM accounts WHERE account_number = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, accountNumber);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapRow(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new DaoException("findByAccountNumber failed", e);
        }
    }

    @Override
    public List<Account> findAll(Connection c) {
        String sql = "SELECT account_number, holder_name, pin, balance, account_type, status, created_at "
                + "FROM accounts ORDER BY account_number";
        List<Account> result = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                result.add(mapRow(rs));
            }
            return result;
        } catch (SQLException e) {
            throw new DaoException("findAll failed", e);
        }
    }

    @Override
    public void updateBalance(Connection c, long accountNumber, BigDecimal newBalance) {
        runUpdate(c, "UPDATE accounts SET balance = ? WHERE account_number = ?",
                ps -> {
                    ps.setBigDecimal(1, newBalance);
                    ps.setLong(2, accountNumber);
                }, "updateBalance");
    }

    @Override
    public void updatePin(Connection c, long accountNumber, String newPinHash) {
        runUpdate(c, "UPDATE accounts SET pin = ? WHERE account_number = ?",
                ps -> {
                    ps.setString(1, newPinHash);
                    ps.setLong(2, accountNumber);
                }, "updatePin");
    }

    @Override
    public void updateStatus(Connection c, long accountNumber, AccountStatus status) {
        runUpdate(c, "UPDATE accounts SET status = ? WHERE account_number = ?",
                ps -> {
                    ps.setString(1, status.name());
                    ps.setLong(2, accountNumber);
                }, "updateStatus");
    }

    private void runUpdate(Connection c, String sql, StatementBinder binder, String label) {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            binder.bind(ps);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DaoException(label + " failed", e);
        }
    }

    private Account mapRow(ResultSet rs) throws SQLException {
        return new Account(
                rs.getLong("account_number"),
                rs.getString("holder_name"),
                rs.getString("pin"),
                rs.getBigDecimal("balance"),
                AccountType.valueOf(rs.getString("account_type")),
                AccountStatus.valueOf(rs.getString("status")),
                rs.getTimestamp("created_at").toLocalDateTime());
    }

    @FunctionalInterface
    private interface StatementBinder {
        void bind(PreparedStatement ps) throws SQLException;
    }
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `mvn -q -Dtest=AccountDaoJdbcTest test`
Expected: PASS (6 tests).

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/bank/dao/AccountDao.java src/main/java/com/bank/dao/AccountDaoJdbc.java src/test/java/com/bank/dao/AccountDaoJdbcTest.java
git commit -m "refactor(dao): make AccountDao connection-aware for transactions"
```

---

## Task 4: Refactor TransactionDao to be Connection-aware

**Files:**
- Modify: `src/main/java/com/bank/dao/TransactionDao.java`
- Modify: `src/main/java/com/bank/dao/TransactionDaoJdbc.java`
- Modify (test): `src/test/java/com/bank/dao/TransactionDaoJdbcTest.java`

**Interfaces:**
- Consumes: `AccountDao`/`AccountDaoJdbc` (Connection-aware, Task 3), `UnitOfWork`, `Database`, `SchemaInitializer`, model + enums.
- Produces `TransactionDao` (Connection-aware):
  - `void insert(Connection c, Transaction transaction)`
  - `List<Transaction> findByAccountNumber(Connection c, long accountNumber)`
  - `List<Transaction> findRecent(Connection c, long accountNumber, int limit)`
  - Implementation `TransactionDaoJdbc()` (no-arg; stateless).

- [ ] **Step 1: Update the test to the new signatures (write it first)**

Replace the entire contents of `src/test/java/com/bank/dao/TransactionDaoJdbcTest.java`:

```java
package com.bank.dao;

import com.bank.db.Database;
import com.bank.db.SchemaInitializer;
import com.bank.db.UnitOfWork;
import com.bank.model.Account;
import com.bank.model.AccountStatus;
import com.bank.model.AccountType;
import com.bank.model.Transaction;
import com.bank.model.TransactionType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TransactionDaoJdbcTest {

    private static final Database db = Database.fromResource("db.test.properties");
    private static final UnitOfWork uow = new UnitOfWork(db);
    private final AccountDao accountDao = new AccountDaoJdbc();
    private final TransactionDao txDao = new TransactionDaoJdbc();

    private static final long ACC = 1000000001L;

    @BeforeAll
    static void createSchema() {
        SchemaInitializer.initialize(db);
    }

    @BeforeEach
    void cleanAndSeedAccount() {
        uow.executeVoid(c -> {
            try (Statement st = c.createStatement()) {
                st.execute("DELETE FROM transactions");
                st.execute("DELETE FROM accounts");
            } catch (SQLException e) {
                throw new DaoException("clean failed", e);
            }
            accountDao.create(c, new Account(ACC, "Asha", "1234", new BigDecimal("100.00"),
                    AccountType.SAVINGS, AccountStatus.ACTIVE, LocalDateTime.now()));
        });
    }

    private Transaction tx(TransactionType type, String amount, String balanceAfter) {
        return new Transaction(0L, ACC, type, new BigDecimal(amount),
                new BigDecimal(balanceAfter), LocalDateTime.now());
    }

    @Test
    void insertAssignsGeneratedId() {
        uow.executeVoid(c -> txDao.insert(c, tx(TransactionType.DEPOSIT, "50.00", "150.00")));
        List<Transaction> found = uow.execute(c -> txDao.findByAccountNumber(c, ACC));
        assertEquals(1, found.size());
        assertTrue(found.get(0).getId() > 0);
        assertEquals(TransactionType.DEPOSIT, found.get(0).getType());
        assertEquals(0, new BigDecimal("50.00").compareTo(found.get(0).getAmount()));
    }

    @Test
    void findByAccountNumberReturnsAllForAccount() {
        uow.executeVoid(c -> {
            txDao.insert(c, tx(TransactionType.DEPOSIT, "50.00", "150.00"));
            txDao.insert(c, tx(TransactionType.WITHDRAW, "20.00", "130.00"));
        });
        assertEquals(2, uow.execute(c -> txDao.findByAccountNumber(c, ACC)).size());
    }

    @Test
    void findRecentRespectsLimitAndNewestFirst() {
        uow.executeVoid(c -> {
            txDao.insert(c, tx(TransactionType.DEPOSIT, "10.00", "110.00"));
            txDao.insert(c, tx(TransactionType.DEPOSIT, "20.00", "130.00"));
            txDao.insert(c, tx(TransactionType.DEPOSIT, "30.00", "160.00"));
        });
        List<Transaction> recent = uow.execute(c -> txDao.findRecent(c, ACC, 2));
        assertEquals(2, recent.size());
        assertTrue(recent.get(0).getId() > recent.get(1).getId());
        assertEquals(0, new BigDecimal("30.00").compareTo(recent.get(0).getAmount()));
    }
}
```

- [ ] **Step 2: Run test to verify it fails to compile**

Run: `mvn -q -Dtest=TransactionDaoJdbcTest test`
Expected: FAIL — compilation errors (old `TransactionDaoJdbc(Database)` / no-`Connection` signatures).

- [ ] **Step 3: Update the `TransactionDao` interface**

Replace `src/main/java/com/bank/dao/TransactionDao.java`:

```java
package com.bank.dao;

import com.bank.model.Transaction;

import java.sql.Connection;
import java.util.List;

public interface TransactionDao {
    void insert(Connection c, Transaction transaction);
    List<Transaction> findByAccountNumber(Connection c, long accountNumber);
    List<Transaction> findRecent(Connection c, long accountNumber, int limit);
}
```

- [ ] **Step 4: Update the `TransactionDaoJdbc` implementation**

Replace `src/main/java/com/bank/dao/TransactionDaoJdbc.java`:

```java
package com.bank.dao;

import com.bank.model.Transaction;
import com.bank.model.TransactionType;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class TransactionDaoJdbc implements TransactionDao {

    @Override
    public void insert(Connection c, Transaction transaction) {
        String sql = "INSERT INTO transactions "
                + "(account_number, type, amount, balance_after) VALUES (?, ?, ?, ?)";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, transaction.getAccountNumber());
            ps.setString(2, transaction.getType().name());
            ps.setBigDecimal(3, transaction.getAmount());
            ps.setBigDecimal(4, transaction.getBalanceAfter());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DaoException("insert transaction failed", e);
        }
    }

    @Override
    public List<Transaction> findByAccountNumber(Connection c, long accountNumber) {
        String sql = "SELECT id, account_number, type, amount, balance_after, `timestamp` "
                + "FROM transactions WHERE account_number = ? ORDER BY id DESC";
        return query(c, sql, accountNumber, null);
    }

    @Override
    public List<Transaction> findRecent(Connection c, long accountNumber, int limit) {
        String sql = "SELECT id, account_number, type, amount, balance_after, `timestamp` "
                + "FROM transactions WHERE account_number = ? ORDER BY id DESC LIMIT ?";
        return query(c, sql, accountNumber, limit);
    }

    private List<Transaction> query(Connection c, String sql, long accountNumber, Integer limit) {
        List<Transaction> result = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, accountNumber);
            if (limit != null) {
                ps.setInt(2, limit);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(mapRow(rs));
                }
            }
            return result;
        } catch (SQLException e) {
            throw new DaoException("query transactions failed", e);
        }
    }

    private Transaction mapRow(ResultSet rs) throws SQLException {
        return new Transaction(
                rs.getLong("id"),
                rs.getLong("account_number"),
                TransactionType.valueOf(rs.getString("type")),
                rs.getBigDecimal("amount"),
                rs.getBigDecimal("balance_after"),
                rs.getTimestamp("timestamp").toLocalDateTime());
    }
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `mvn -q -Dtest=TransactionDaoJdbcTest test`
Expected: PASS (3 tests).

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/bank/dao/TransactionDao.java src/main/java/com/bank/dao/TransactionDaoJdbc.java src/test/java/com/bank/dao/TransactionDaoJdbcTest.java
git commit -m "refactor(dao): make TransactionDao connection-aware for transactions"
```

---

## Task 5: Admin model + AdminDao

**Files:**
- Create: `src/main/java/com/bank/model/Admin.java`
- Create: `src/main/java/com/bank/dao/AdminDao.java`
- Create: `src/main/java/com/bank/dao/AdminDaoJdbc.java`
- Test: `src/test/java/com/bank/dao/AdminDaoJdbcTest.java`

**Interfaces:**
- Consumes: `UnitOfWork`, `Database`, `SchemaInitializer`, `DaoException`.
- Produces:
  - `Admin(long id, String username, String passwordHash)` with `getId()`, `getUsername()`, `getPasswordHash()`.
  - `AdminDao`: `Optional<Admin> findByUsername(Connection c, String username)`, `void create(Connection c, Admin admin)`.
  - Implementation `AdminDaoJdbc()`.

- [ ] **Step 1: Write the failing test**

`src/test/java/com/bank/dao/AdminDaoJdbcTest.java`:

```java
package com.bank.dao;

import com.bank.db.Database;
import com.bank.db.SchemaInitializer;
import com.bank.db.UnitOfWork;
import com.bank.model.Admin;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.sql.Statement;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class AdminDaoJdbcTest {

    private static final Database db = Database.fromResource("db.test.properties");
    private static final UnitOfWork uow = new UnitOfWork(db);
    private final AdminDao dao = new AdminDaoJdbc();

    @BeforeAll
    static void createSchema() {
        SchemaInitializer.initialize(db);
    }

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

    @Test
    void createThenFindByUsername() {
        uow.executeVoid(c -> dao.create(c, new Admin(0L, "manager", "hashed-pw")));
        Optional<Admin> found = uow.execute(c -> dao.findByUsername(c, "manager"));
        assertTrue(found.isPresent());
        assertEquals("manager", found.get().getUsername());
        assertEquals("hashed-pw", found.get().getPasswordHash());
        assertTrue(found.get().getId() > 0);
    }

    @Test
    void findByUsernameReturnsEmptyWhenAbsent() {
        assertTrue(uow.execute(c -> dao.findByUsername(c, "nobody")).isEmpty());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q -Dtest=AdminDaoJdbcTest test`
Expected: FAIL — cannot find symbol `Admin` / `AdminDao`.

- [ ] **Step 3: Create the `Admin` model**

`src/main/java/com/bank/model/Admin.java`:

```java
package com.bank.model;

public class Admin {
    private final long id;
    private final String username;
    private final String passwordHash;

    public Admin(long id, String username, String passwordHash) {
        this.id = id;
        this.username = username;
        this.passwordHash = passwordHash;
    }

    public long getId() { return id; }
    public String getUsername() { return username; }
    public String getPasswordHash() { return passwordHash; }

    @Override
    public String toString() {
        return "Admin{id=" + id + ", username='" + username + "'}";
    }
}
```

- [ ] **Step 4: Create the `AdminDao` interface**

`src/main/java/com/bank/dao/AdminDao.java`:

```java
package com.bank.dao;

import com.bank.model.Admin;

import java.sql.Connection;
import java.util.Optional;

public interface AdminDao {
    Optional<Admin> findByUsername(Connection c, String username);
    void create(Connection c, Admin admin);
}
```

- [ ] **Step 5: Create the `AdminDaoJdbc` implementation**

`src/main/java/com/bank/dao/AdminDaoJdbc.java`:

```java
package com.bank.dao;

import com.bank.model.Admin;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

public class AdminDaoJdbc implements AdminDao {

    @Override
    public Optional<Admin> findByUsername(Connection c, String username) {
        String sql = "SELECT id, username, password FROM admins WHERE username = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(new Admin(
                        rs.getLong("id"),
                        rs.getString("username"),
                        rs.getString("password")));
            }
        } catch (SQLException e) {
            throw new DaoException("findByUsername failed", e);
        }
    }

    @Override
    public void create(Connection c, Admin admin) {
        String sql = "INSERT INTO admins (username, password) VALUES (?, ?)";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, admin.getUsername());
            ps.setString(2, admin.getPasswordHash());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DaoException("create admin failed", e);
        }
    }
}
```

- [ ] **Step 6: Run test to verify it passes**

Run: `mvn -q -Dtest=AdminDaoJdbcTest test`
Expected: PASS (2 tests).

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/bank/model/Admin.java src/main/java/com/bank/dao/AdminDao.java src/main/java/com/bank/dao/AdminDaoJdbc.java src/test/java/com/bank/dao/AdminDaoJdbcTest.java
git commit -m "feat(dao): add Admin model and AdminDao"
```

---

## Task 6: Service exception hierarchy

**Files:**
- Create: `src/main/java/com/bank/service/BankServiceException.java`
- Create: `src/main/java/com/bank/service/AccountNotFoundException.java`
- Create: `src/main/java/com/bank/service/AccountNotActiveException.java`
- Create: `src/main/java/com/bank/service/InsufficientFundsException.java`
- Create: `src/main/java/com/bank/service/InvalidAmountException.java`
- Create: `src/main/java/com/bank/service/InvalidPinException.java`
- Create: `src/main/java/com/bank/service/AuthenticationException.java`
- Test: `src/test/java/com/bank/service/ExceptionHierarchyTest.java`

**Interfaces:**
- Consumes: nothing.
- Produces: `BankServiceException extends RuntimeException` with a `String` constructor; six subtypes each with a `String` constructor, all assignable to `BankServiceException`.

- [ ] **Step 1: Write the failing test**

`src/test/java/com/bank/service/ExceptionHierarchyTest.java`:

```java
package com.bank.service;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ExceptionHierarchyTest {
    @Test
    void allSubtypesAreBankServiceExceptions() {
        assertTrue(new AccountNotFoundException("x") instanceof BankServiceException);
        assertTrue(new AccountNotActiveException("x") instanceof BankServiceException);
        assertTrue(new InsufficientFundsException("x") instanceof BankServiceException);
        assertTrue(new InvalidAmountException("x") instanceof BankServiceException);
        assertTrue(new InvalidPinException("x") instanceof BankServiceException);
        assertTrue(new AuthenticationException("x") instanceof BankServiceException);
        assertTrue(new BankServiceException("x") instanceof RuntimeException);
    }

    @Test
    void messageIsPreserved() {
        assertEquals("nope", new InsufficientFundsException("nope").getMessage());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q -Dtest=ExceptionHierarchyTest test`
Expected: FAIL — cannot find symbol `BankServiceException`.

- [ ] **Step 3: Create the base exception**

`src/main/java/com/bank/service/BankServiceException.java`:

```java
package com.bank.service;

public class BankServiceException extends RuntimeException {
    public BankServiceException(String message) {
        super(message);
    }
}
```

- [ ] **Step 4: Create the six subtypes**

Each file is the same shape. `AccountNotFoundException.java`:

```java
package com.bank.service;

public class AccountNotFoundException extends BankServiceException {
    public AccountNotFoundException(String message) {
        super(message);
    }
}
```

`AccountNotActiveException.java`:

```java
package com.bank.service;

public class AccountNotActiveException extends BankServiceException {
    public AccountNotActiveException(String message) {
        super(message);
    }
}
```

`InsufficientFundsException.java`:

```java
package com.bank.service;

public class InsufficientFundsException extends BankServiceException {
    public InsufficientFundsException(String message) {
        super(message);
    }
}
```

`InvalidAmountException.java`:

```java
package com.bank.service;

public class InvalidAmountException extends BankServiceException {
    public InvalidAmountException(String message) {
        super(message);
    }
}
```

`InvalidPinException.java`:

```java
package com.bank.service;

public class InvalidPinException extends BankServiceException {
    public InvalidPinException(String message) {
        super(message);
    }
}
```

`AuthenticationException.java`:

```java
package com.bank.service;

public class AuthenticationException extends BankServiceException {
    public AuthenticationException(String message) {
        super(message);
    }
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `mvn -q -Dtest=ExceptionHierarchyTest test`
Expected: PASS (2 tests).

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/bank/service/*.java src/test/java/com/bank/service/ExceptionHierarchyTest.java
git commit -m "feat(service): add BankServiceException hierarchy"
```

---

## Task 7: AccountService — open account + money operations

**Files:**
- Create: `src/main/java/com/bank/service/AccountService.java`
- Test: `src/test/java/com/bank/service/AccountServiceTest.java`

**Interfaces:**
- Consumes: `UnitOfWork`, `AccountDao`/`AccountDaoJdbc`, `TransactionDao`/`TransactionDaoJdbc` (Connection-aware), `PasswordHasher`, model + enums, the exception hierarchy.
- Produces `AccountService(UnitOfWork, AccountDao, TransactionDao, PasswordHasher)` with:
  - `Account openAccount(String holderName, String rawPin, AccountType type, BigDecimal openingBalance)`
  - `BigDecimal getBalance(long acct)`
  - `void deposit(long acct, BigDecimal amount)`
  - `void withdraw(long acct, BigDecimal amount)`
  - `void transfer(long from, long to, BigDecimal amount)`
  - `List<Transaction> miniStatement(long acct, int n)`
  - (private helpers `loadOrThrow`, `requireActive`, `requirePositive`, `requireValidPin`, `scale`, `generateUniqueAccountNumber` — also used by Task 8.)

- [ ] **Step 1: Write the failing test**

`src/test/java/com/bank/service/AccountServiceTest.java`:

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
import com.bank.model.Transaction;
import com.bank.security.PasswordHasher;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AccountServiceTest {

    private static final Database db = Database.fromResource("db.test.properties");
    private static final UnitOfWork uow = new UnitOfWork(db);
    private final AccountService service =
            new AccountService(uow, new AccountDaoJdbc(), new TransactionDaoJdbc(), new PasswordHasher());

    @BeforeAll
    static void schema() { SchemaInitializer.initialize(db); }

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

    private Account open(String balance) {
        return service.openAccount("Asha", "1234", AccountType.SAVINGS, new BigDecimal(balance));
    }

    @Test
    void openAccountGeneratesNumberHashesPinAndSetsBalance() {
        Account a = open("100.00");
        assertTrue(a.getAccountNumber() >= 1_000_000_000L);
        assertNotEquals("1234", a.getPin(), "PIN must be hashed, not plaintext");
        assertEquals(0, new BigDecimal("100.00").compareTo(service.getBalance(a.getAccountNumber())));
    }

    @Test
    void openAccountRejectsBadPin() {
        assertThrows(InvalidPinException.class,
                () -> service.openAccount("X", "12", AccountType.SAVINGS, BigDecimal.ZERO));
    }

    @Test
    void depositIncreasesBalanceAndRecordsTransaction() {
        Account a = open("50.00");
        service.deposit(a.getAccountNumber(), new BigDecimal("25.50"));
        assertEquals(0, new BigDecimal("75.50").compareTo(service.getBalance(a.getAccountNumber())));
        List<Transaction> tx = service.miniStatement(a.getAccountNumber(), 10);
        assertFalse(tx.isEmpty());
    }

    @Test
    void depositRejectsNonPositiveAmount() {
        Account a = open("50.00");
        assertThrows(InvalidAmountException.class,
                () -> service.deposit(a.getAccountNumber(), new BigDecimal("0.00")));
    }

    @Test
    void withdrawDecreasesBalance() {
        Account a = open("50.00");
        service.withdraw(a.getAccountNumber(), new BigDecimal("20.00"));
        assertEquals(0, new BigDecimal("30.00").compareTo(service.getBalance(a.getAccountNumber())));
    }

    @Test
    void withdrawOverBalanceIsRejected() {
        Account a = open("50.00");
        assertThrows(InsufficientFundsException.class,
                () -> service.withdraw(a.getAccountNumber(), new BigDecimal("50.01")));
        assertEquals(0, new BigDecimal("50.00").compareTo(service.getBalance(a.getAccountNumber())));
    }

    @Test
    void getBalanceUnknownAccountThrows() {
        assertThrows(AccountNotFoundException.class, () -> service.getBalance(1L));
    }

    @Test
    void transferMovesMoneyAtomically() {
        Account from = open("100.00");
        Account to = open("0.00");
        service.transfer(from.getAccountNumber(), to.getAccountNumber(), new BigDecimal("40.00"));
        assertEquals(0, new BigDecimal("60.00").compareTo(service.getBalance(from.getAccountNumber())));
        assertEquals(0, new BigDecimal("40.00").compareTo(service.getBalance(to.getAccountNumber())));
    }

    @Test
    void transferWithInsufficientFundsRollsBackBothBalances() {
        Account from = open("30.00");
        Account to = open("10.00");
        assertThrows(InsufficientFundsException.class,
                () -> service.transfer(from.getAccountNumber(), to.getAccountNumber(), new BigDecimal("40.00")));
        assertEquals(0, new BigDecimal("30.00").compareTo(service.getBalance(from.getAccountNumber())));
        assertEquals(0, new BigDecimal("10.00").compareTo(service.getBalance(to.getAccountNumber())));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q -Dtest=AccountServiceTest test`
Expected: FAIL — cannot find symbol `AccountService`.

- [ ] **Step 3: Implement `AccountService`**

`src/main/java/com/bank/service/AccountService.java`:

```java
package com.bank.service;

import com.bank.dao.AccountDao;
import com.bank.dao.TransactionDao;
import com.bank.db.UnitOfWork;
import com.bank.model.Account;
import com.bank.model.AccountStatus;
import com.bank.model.AccountType;
import com.bank.model.Transaction;
import com.bank.model.TransactionType;
import com.bank.security.PasswordHasher;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Random;

public class AccountService {

    private final UnitOfWork uow;
    private final AccountDao accountDao;
    private final TransactionDao transactionDao;
    private final PasswordHasher hasher;
    private final Random random = new Random();

    public AccountService(UnitOfWork uow, AccountDao accountDao,
                          TransactionDao transactionDao, PasswordHasher hasher) {
        this.uow = uow;
        this.accountDao = accountDao;
        this.transactionDao = transactionDao;
        this.hasher = hasher;
    }

    public Account openAccount(String holderName, String rawPin, AccountType type, BigDecimal openingBalance) {
        requireValidPin(rawPin);
        if (openingBalance == null || openingBalance.signum() < 0) {
            throw new InvalidAmountException("opening balance must be >= 0");
        }
        BigDecimal opening = scale(openingBalance);
        return uow.execute(c -> {
            long number = generateUniqueAccountNumber(c);
            Account account = new Account(number, holderName, hasher.hash(rawPin), opening,
                    type, AccountStatus.ACTIVE, LocalDateTime.now());
            accountDao.create(c, account);
            if (opening.signum() > 0) {
                transactionDao.insert(c, new Transaction(0L, number, TransactionType.DEPOSIT,
                        opening, opening, LocalDateTime.now()));
            }
            return account;
        });
    }

    public BigDecimal getBalance(long acct) {
        return uow.execute(c -> loadOrThrow(c, acct).getBalance());
    }

    public void deposit(long acct, BigDecimal amount) {
        requirePositive(amount);
        BigDecimal amt = scale(amount);
        uow.executeVoid(c -> {
            Account a = loadOrThrow(c, acct);
            requireActive(a);
            BigDecimal newBalance = scale(a.getBalance().add(amt));
            accountDao.updateBalance(c, acct, newBalance);
            transactionDao.insert(c, new Transaction(0L, acct, TransactionType.DEPOSIT,
                    amt, newBalance, LocalDateTime.now()));
        });
    }

    public void withdraw(long acct, BigDecimal amount) {
        requirePositive(amount);
        BigDecimal amt = scale(amount);
        uow.executeVoid(c -> {
            Account a = loadOrThrow(c, acct);
            requireActive(a);
            if (a.getBalance().compareTo(amt) < 0) {
                throw new InsufficientFundsException("insufficient funds");
            }
            BigDecimal newBalance = scale(a.getBalance().subtract(amt));
            accountDao.updateBalance(c, acct, newBalance);
            transactionDao.insert(c, new Transaction(0L, acct, TransactionType.WITHDRAW,
                    amt, newBalance, LocalDateTime.now()));
        });
    }

    public void transfer(long from, long to, BigDecimal amount) {
        requirePositive(amount);
        if (from == to) {
            throw new InvalidAmountException("cannot transfer to the same account");
        }
        BigDecimal amt = scale(amount);
        uow.executeVoid(c -> {
            Account src = loadOrThrow(c, from);
            Account dst = loadOrThrow(c, to);
            requireActive(src);
            requireActive(dst);
            if (src.getBalance().compareTo(amt) < 0) {
                throw new InsufficientFundsException("insufficient funds");
            }
            BigDecimal newSrc = scale(src.getBalance().subtract(amt));
            BigDecimal newDst = scale(dst.getBalance().add(amt));
            accountDao.updateBalance(c, from, newSrc);
            accountDao.updateBalance(c, to, newDst);
            transactionDao.insert(c, new Transaction(0L, from, TransactionType.TRANSFER,
                    amt, newSrc, LocalDateTime.now()));
            transactionDao.insert(c, new Transaction(0L, to, TransactionType.TRANSFER,
                    amt, newDst, LocalDateTime.now()));
        });
    }

    public List<Transaction> miniStatement(long acct, int n) {
        return uow.execute(c -> {
            loadOrThrow(c, acct);
            return transactionDao.findRecent(c, acct, n);
        });
    }

    // ---- helpers (shared with lifecycle/PIN operations) ----

    Account loadOrThrow(Connection c, long acct) {
        return accountDao.findByAccountNumber(c, acct)
                .orElseThrow(() -> new AccountNotFoundException("no account " + acct));
    }

    void requireActive(Account a) {
        if (a.getStatus() != AccountStatus.ACTIVE) {
            throw new AccountNotActiveException("account " + a.getAccountNumber() + " is " + a.getStatus());
        }
    }

    void requirePositive(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new InvalidAmountException("amount must be > 0");
        }
    }

    void requireValidPin(String pin) {
        if (pin == null || !pin.matches("\\d{4}")) {
            throw new InvalidPinException("PIN must be exactly 4 digits");
        }
    }

    BigDecimal scale(BigDecimal v) {
        return v.setScale(2, RoundingMode.HALF_UP);
    }

    private long generateUniqueAccountNumber(Connection c) {
        long number;
        do {
            number = 1_000_000_000L + (long) (random.nextDouble() * 9_000_000_000L);
        } while (accountDao.findByAccountNumber(c, number).isPresent());
        return number;
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -q -Dtest=AccountServiceTest test`
Expected: PASS (10 tests) — including the rollback assertion on the failed transfer.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/bank/service/AccountService.java src/test/java/com/bank/service/AccountServiceTest.java
git commit -m "feat(service): add AccountService open + money operations"
```

---

## Task 8: AccountService — changePin, block, close

**Files:**
- Modify: `src/main/java/com/bank/service/AccountService.java`
- Test: `src/test/java/com/bank/service/AccountLifecycleTest.java`

**Interfaces:**
- Consumes: everything from Task 7 (same `AccountService` instance, its helpers, `PasswordHasher`).
- Produces (added to `AccountService`):
  - `void changePin(long acct, String oldPin, String newPin)`
  - `void blockAccount(long acct)`
  - `void closeAccount(long acct)`

- [ ] **Step 1: Write the failing test**

`src/test/java/com/bank/service/AccountLifecycleTest.java`:

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

class AccountLifecycleTest {

    private static final Database db = Database.fromResource("db.test.properties");
    private static final UnitOfWork uow = new UnitOfWork(db);
    private final AccountService service =
            new AccountService(uow, new AccountDaoJdbc(), new TransactionDaoJdbc(), new PasswordHasher());

    @BeforeAll
    static void schema() { SchemaInitializer.initialize(db); }

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

    private Account open() {
        return service.openAccount("Asha", "1234", AccountType.SAVINGS, new BigDecimal("100.00"));
    }

    @Test
    void changePinWithCorrectOldPinSucceeds() {
        Account a = open();
        service.changePin(a.getAccountNumber(), "1234", "5678");
        // new PIN works for a subsequent change; old PIN no longer does
        assertThrows(AuthenticationException.class,
                () -> service.changePin(a.getAccountNumber(), "1234", "0000"));
        service.changePin(a.getAccountNumber(), "5678", "0000"); // no throw
    }

    @Test
    void changePinRejectsWrongOldPin() {
        Account a = open();
        assertThrows(AuthenticationException.class,
                () -> service.changePin(a.getAccountNumber(), "0000", "5678"));
    }

    @Test
    void changePinRejectsBadNewPin() {
        Account a = open();
        assertThrows(InvalidPinException.class,
                () -> service.changePin(a.getAccountNumber(), "1234", "abc"));
    }

    @Test
    void blockedAccountCannotTransact() {
        Account a = open();
        service.blockAccount(a.getAccountNumber());
        assertThrows(AccountNotActiveException.class,
                () -> service.withdraw(a.getAccountNumber(), new BigDecimal("10.00")));
    }

    @Test
    void closedAccountCannotTransact() {
        Account a = open();
        service.closeAccount(a.getAccountNumber());
        assertThrows(AccountNotActiveException.class,
                () -> service.deposit(a.getAccountNumber(), new BigDecimal("10.00")));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q -Dtest=AccountLifecycleTest test`
Expected: FAIL — `changePin` / `blockAccount` / `closeAccount` not found.

- [ ] **Step 3: Add the three methods to `AccountService`**

Insert these methods into `src/main/java/com/bank/service/AccountService.java` (after `miniStatement`, before the `// ---- helpers` section):

```java
    public void changePin(long acct, String oldPin, String newPin) {
        requireValidPin(newPin);
        uow.executeVoid(c -> {
            Account a = loadOrThrow(c, acct);
            if (!hasher.verify(oldPin, a.getPin())) {
                throw new AuthenticationException("current PIN is incorrect");
            }
            accountDao.updatePin(c, acct, hasher.hash(newPin));
        });
    }

    public void blockAccount(long acct) {
        uow.executeVoid(c -> {
            loadOrThrow(c, acct);
            accountDao.updateStatus(c, acct, AccountStatus.BLOCKED);
        });
    }

    public void closeAccount(long acct) {
        uow.executeVoid(c -> {
            loadOrThrow(c, acct);
            accountDao.updateStatus(c, acct, AccountStatus.CLOSED);
        });
    }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -q -Dtest=AccountLifecycleTest test`
Expected: PASS (5 tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/bank/service/AccountService.java src/test/java/com/bank/service/AccountLifecycleTest.java
git commit -m "feat(service): add changePin, block, and close operations"
```

---

## Task 9: AuthService

**Files:**
- Create: `src/main/java/com/bank/service/AuthService.java`
- Test: `src/test/java/com/bank/service/AuthServiceTest.java`

**Interfaces:**
- Consumes: `UnitOfWork`, `AccountDao`/`AccountDaoJdbc`, `AdminDao`/`AdminDaoJdbc`, `PasswordHasher`, `AccountService` (to open a test account), `Admin`, model + enums, the exception hierarchy.
- Produces `AuthService(UnitOfWork, AccountDao, AdminDao, PasswordHasher)` with:
  - `Account authenticateCustomer(long acct, String rawPin)`
  - `Admin authenticateAdmin(String username, String rawPassword)`

- [ ] **Step 1: Write the failing test**

`src/test/java/com/bank/service/AuthServiceTest.java`:

```java
package com.bank.service;

import com.bank.dao.AccountDaoJdbc;
import com.bank.dao.AdminDaoJdbc;
import com.bank.dao.DaoException;
import com.bank.dao.TransactionDaoJdbc;
import com.bank.db.Database;
import com.bank.db.SchemaInitializer;
import com.bank.db.UnitOfWork;
import com.bank.model.Account;
import com.bank.model.Admin;
import com.bank.model.AccountType;
import com.bank.security.PasswordHasher;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

class AuthServiceTest {

    private static final Database db = Database.fromResource("db.test.properties");
    private static final UnitOfWork uow = new UnitOfWork(db);
    private static final PasswordHasher hasher = new PasswordHasher();

    private final AccountDaoJdbc accountDao = new AccountDaoJdbc();
    private final AdminDaoJdbc adminDao = new AdminDaoJdbc();
    private final AccountService accountService =
            new AccountService(uow, accountDao, new TransactionDaoJdbc(), hasher);
    private final AuthService auth = new AuthService(uow, accountDao, adminDao, hasher);

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

    private Account openActive() {
        return accountService.openAccount("Asha", "1234", AccountType.SAVINGS, new BigDecimal("0.00"));
    }

    @Test
    void authenticateCustomerSucceedsWithCorrectPin() {
        Account a = openActive();
        Account result = auth.authenticateCustomer(a.getAccountNumber(), "1234");
        assertEquals(a.getAccountNumber(), result.getAccountNumber());
    }

    @Test
    void authenticateCustomerFailsWithWrongPin() {
        Account a = openActive();
        assertThrows(AuthenticationException.class,
                () -> auth.authenticateCustomer(a.getAccountNumber(), "0000"));
    }

    @Test
    void authenticateCustomerFailsForUnknownAccount() {
        assertThrows(AuthenticationException.class,
                () -> auth.authenticateCustomer(123L, "1234"));
    }

    @Test
    void authenticateCustomerRejectsBlockedAccount() {
        Account a = openActive();
        accountService.blockAccount(a.getAccountNumber());
        assertThrows(AccountNotActiveException.class,
                () -> auth.authenticateCustomer(a.getAccountNumber(), "1234"));
    }

    @Test
    void authenticateAdminSucceedsAndFails() {
        uow.executeVoid(c -> adminDao.create(c, new Admin(0L, "manager", hasher.hash("secret"))));
        assertEquals("manager", auth.authenticateAdmin("manager", "secret").getUsername());
        assertThrows(AuthenticationException.class, () -> auth.authenticateAdmin("manager", "wrong"));
        assertThrows(AuthenticationException.class, () -> auth.authenticateAdmin("ghost", "secret"));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q -Dtest=AuthServiceTest test`
Expected: FAIL — cannot find symbol `AuthService`.

- [ ] **Step 3: Implement `AuthService`**

`src/main/java/com/bank/service/AuthService.java`:

```java
package com.bank.service;

import com.bank.dao.AccountDao;
import com.bank.dao.AdminDao;
import com.bank.db.UnitOfWork;
import com.bank.model.Account;
import com.bank.model.AccountStatus;
import com.bank.model.Admin;
import com.bank.security.PasswordHasher;

public class AuthService {

    private final UnitOfWork uow;
    private final AccountDao accountDao;
    private final AdminDao adminDao;
    private final PasswordHasher hasher;

    public AuthService(UnitOfWork uow, AccountDao accountDao, AdminDao adminDao, PasswordHasher hasher) {
        this.uow = uow;
        this.accountDao = accountDao;
        this.adminDao = adminDao;
        this.hasher = hasher;
    }

    public Account authenticateCustomer(long acct, String rawPin) {
        return uow.execute(c -> {
            Account account = accountDao.findByAccountNumber(c, acct).orElse(null);
            if (account == null || !hasher.verify(rawPin, account.getPin())) {
                throw new AuthenticationException("invalid account number or PIN");
            }
            if (account.getStatus() != AccountStatus.ACTIVE) {
                throw new AccountNotActiveException("account is " + account.getStatus());
            }
            return account;
        });
    }

    public Admin authenticateAdmin(String username, String rawPassword) {
        return uow.execute(c -> {
            Admin admin = adminDao.findByUsername(c, username).orElse(null);
            if (admin == null || !hasher.verify(rawPassword, admin.getPasswordHash())) {
                throw new AuthenticationException("invalid username or password");
            }
            return admin;
        });
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -q -Dtest=AuthServiceTest test`
Expected: PASS (5 tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/bank/service/AuthService.java src/test/java/com/bank/service/AuthServiceTest.java
git commit -m "feat(service): add AuthService for customer and admin login"
```

---

## Task 10: Full suite + README update

**Files:**
- Modify: `README.md`

**Interfaces:**
- Consumes: everything above.
- Produces: green full suite + updated docs.

- [ ] **Step 1: Run the whole suite**

Run: `mvn test`
Expected: BUILD SUCCESS — all Stage 1 (updated) and Stage 2 tests pass (model, db, dao, security, service).

- [ ] **Step 2: Update `README.md`**

Replace the "**Stage 1 (current):**" line and the "## Layout" section in `README.md` with:

```markdown
**Stage 2 (current):** service layer — transactional business logic, authentication, BCrypt PIN/password hashing.
Stage 1 (done): database foundation — schema, connection, model, DAO layer.

## Layout
- `com.bank.model`    — Account, Transaction, Admin, enums (data carriers)
- `com.bank.db`       — Database (config/connection), SchemaInitializer, UnitOfWork (transactions)
- `com.bank.dao`      — AccountDao / TransactionDao / AdminDao (connection-aware JDBC)
- `com.bank.security` — PasswordHasher (BCrypt)
- `com.bank.service`  — AccountService, AuthService, BankServiceException hierarchy
```

- [ ] **Step 3: Commit**

```bash
git add README.md
git commit -m "docs: document Stage 2 service layer"
```

---

## Self-Review Notes

- **Spec coverage:** §2 packages → all tasks; §3 UnitOfWork+DAO refactor → Tasks 2–4; §4 Admin/AdminDao → Task 5; §5 PasswordHasher → Task 1; §6 AccountService methods → Tasks 7–8, AuthService → Task 9; §7 exception hierarchy → Task 6 (incl. `InvalidPinException`); §8 testing (PasswordHasher, UnitOfWork rollback, service happy+failure paths, updated DAO tests) → Tasks 1–9; §9 jBCrypt dep → Task 1; §10 success criteria → Task 10 full-suite run. All covered.
- **Placeholder scan:** no TBD/TODO; complete code in every step.
- **Type consistency:** DAO signatures (all take `Connection` first) are identical across Tasks 3–5 and their consumers in Tasks 7–9; `UnitOfWork.execute`/`executeVoid`, `PasswordHasher.hash`/`verify`, service constructors, and exception names match everywhere. `AccountDaoJdbc()`/`TransactionDaoJdbc()`/`AdminDaoJdbc()` are all no-arg.
- **Atomicity:** `transfer` and the failed-transfer rollback are covered by `AccountServiceTest`; the `UnitOfWork` rollback primitive is proven independently in Task 2.
- **`timestamp` reserved word:** the refactored `TransactionDaoJdbc` keeps the Stage 1 backtick-quoting in its SELECTs.

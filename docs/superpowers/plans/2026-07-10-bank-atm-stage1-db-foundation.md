# Stage 1 — Database Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the tested data-access foundation (MySQL schema, connection/config, model POJOs, JDBC DAO layer) for the Bank Management / ATM Simulator.

**Architecture:** Clean layered structure — `db` (connection + schema), `model` (data carriers), `dao` (JDBC data access). No business rules and no UI in this stage. Later stages add `service` and JavaFX `ui` layers on top.

**Tech Stack:** Java 17, Maven, MySQL 9.x (Connector/J), JUnit 5, JDBC with `PreparedStatement`.

## Global Constraints

- Java 17 (Temurin 17.0.19 installed); Maven 3.9.16; MySQL Community Server 9.7.
- Money is `java.math.BigDecimal` in Java and `DECIMAL(15,2)` in SQL — never `double`/`float`.
- All SQL runs through `PreparedStatement` — no string concatenation of values.
- DAO layer does **data access only** — no business rules, no validation, no auth, no PIN hashing.
- Credentials live in `db.properties` (main) / `db.test.properties` (test) — never hardcoded in `.java`.
- Enums are stored in the DB as their `name()` string; parsed back with `valueOf()`.
- Tests run against a real separate MySQL database `bank_test` (not `bank`, not H2).
- Package root: `com.bank`. Maven groupId `com.bank`, artifactId `bank-atm-simulator`.

## Prerequisite (one-time manual, done by the human before running tests)

In MySQL, create two empty databases (tables are created by `SchemaInitializer`):

```sql
CREATE DATABASE IF NOT EXISTS bank;
CREATE DATABASE IF NOT EXISTS bank_test;
```

Set real credentials in `src/main/resources/db.properties` and `src/test/resources/db.test.properties`.

---

## File Structure

- Create: `pom.xml` — Maven build, dependencies (Connector/J, JUnit 5).
- Create: `src/main/resources/db.properties` — main DB config (gitignored).
- Create: `src/main/resources/schema.sql` — `CREATE TABLE IF NOT EXISTS` for all 3 tables.
- Create: `src/main/java/com/bank/dao/DaoException.java` — unchecked SQL wrapper.
- Create: `src/main/java/com/bank/db/Database.java` — reads config, hands out `Connection`.
- Create: `src/main/java/com/bank/db/SchemaInitializer.java` — runs `schema.sql`.
- Create: `src/main/java/com/bank/model/AccountType.java`, `AccountStatus.java`, `TransactionType.java` — enums.
- Create: `src/main/java/com/bank/model/Account.java`, `Transaction.java` — POJOs.
- Create: `src/main/java/com/bank/dao/AccountDao.java`, `AccountDaoJdbc.java`.
- Create: `src/main/java/com/bank/dao/TransactionDao.java`, `TransactionDaoJdbc.java`.
- Create: `src/test/resources/db.test.properties` — points at `bank_test`.
- Create: `src/test/java/com/bank/db/SchemaInitializerTest.java`.
- Create: `src/test/java/com/bank/dao/AccountDaoJdbcTest.java`.
- Create: `src/test/java/com/bank/dao/TransactionDaoJdbcTest.java`.

---

## Task 1: Maven project + build config

**Files:**
- Create: `pom.xml`
- Create: `src/main/resources/db.properties`
- Create: `src/test/resources/db.test.properties`

**Interfaces:**
- Consumes: nothing.
- Produces: a compiling Maven project with Connector/J + JUnit 5 on the classpath; config resources `db.properties` and `db.test.properties`.

- [ ] **Step 1: Create `pom.xml`**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>com.bank</groupId>
    <artifactId>bank-atm-simulator</artifactId>
    <version>1.0-SNAPSHOT</version>
    <packaging>jar</packaging>

    <properties>
        <maven.compiler.release>17</maven.compiler.release>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    </properties>

    <dependencies>
        <dependency>
            <groupId>com.mysql</groupId>
            <artifactId>mysql-connector-j</artifactId>
            <version>9.1.0</version>
        </dependency>
        <dependency>
            <groupId>org.junit.jupiter</groupId>
            <artifactId>junit-jupiter</artifactId>
            <version>5.10.2</version>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-surefire-plugin</artifactId>
                <version>3.2.5</version>
            </plugin>
        </plugins>
    </build>
</project>
```

- [ ] **Step 2: Create `src/main/resources/db.properties`**

```properties
db.url=jdbc:mysql://localhost:3306/bank
db.user=root
db.password=
```

- [ ] **Step 3: Create `src/test/resources/db.test.properties`**

```properties
db.url=jdbc:mysql://localhost:3306/bank_test
db.user=root
db.password=
```

- [ ] **Step 4: Verify the project compiles and dependencies resolve**

Run: `mvn -q compile`
Expected: BUILD SUCCESS, no compile errors (empty source tree is fine).

- [ ] **Step 5: Commit**

```bash
git add pom.xml src/main/resources/db.properties src/test/resources/db.test.properties
git commit -m "build: Maven project with Connector/J and JUnit 5"
```

> Note: `db.properties` is listed in `.gitignore`. Use `git add -f src/main/resources/db.properties` for this first commit so the template (with empty password) is tracked, OR keep it ignored and commit only `db.test.properties`. Recommended: keep `db.properties` ignored; commit `db.test.properties` only.

---

## Task 2: Enums

**Files:**
- Create: `src/main/java/com/bank/model/AccountType.java`
- Create: `src/main/java/com/bank/model/AccountStatus.java`
- Create: `src/main/java/com/bank/model/TransactionType.java`
- Test: `src/test/java/com/bank/model/EnumTest.java`

**Interfaces:**
- Consumes: nothing.
- Produces: `AccountType {SAVINGS, CURRENT}`, `AccountStatus {ACTIVE, BLOCKED, CLOSED}`, `TransactionType {DEPOSIT, WITHDRAW, TRANSFER}`.

- [ ] **Step 1: Write the failing test**

`src/test/java/com/bank/model/EnumTest.java`:

```java
package com.bank.model;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class EnumTest {
    @Test
    void enumsRoundTripThroughName() {
        assertEquals(AccountType.SAVINGS, AccountType.valueOf("SAVINGS"));
        assertEquals(AccountStatus.ACTIVE, AccountStatus.valueOf("ACTIVE"));
        assertEquals(TransactionType.DEPOSIT, TransactionType.valueOf("DEPOSIT"));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q -Dtest=EnumTest test`
Expected: FAIL — cannot find symbol `AccountType`.

- [ ] **Step 3: Write the enums**

`src/main/java/com/bank/model/AccountType.java`:

```java
package com.bank.model;

public enum AccountType {
    SAVINGS, CURRENT
}
```

`src/main/java/com/bank/model/AccountStatus.java`:

```java
package com.bank.model;

public enum AccountStatus {
    ACTIVE, BLOCKED, CLOSED
}
```

`src/main/java/com/bank/model/TransactionType.java`:

```java
package com.bank.model;

public enum TransactionType {
    DEPOSIT, WITHDRAW, TRANSFER
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -q -Dtest=EnumTest test`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/bank/model src/test/java/com/bank/model/EnumTest.java
git commit -m "feat(model): add AccountType, AccountStatus, TransactionType enums"
```

---

## Task 3: Model POJOs (Account, Transaction)

**Files:**
- Create: `src/main/java/com/bank/model/Account.java`
- Create: `src/main/java/com/bank/model/Transaction.java`
- Test: `src/test/java/com/bank/model/AccountTest.java`

**Interfaces:**
- Consumes: `AccountType`, `AccountStatus`, `TransactionType` (Task 2).
- Produces:
  - `Account(long accountNumber, String holderName, String pin, BigDecimal balance, AccountType accountType, AccountStatus status, LocalDateTime createdAt)` with getters `getAccountNumber()`, `getHolderName()`, `getPin()`, `getBalance()`, `getAccountType()`, `getStatus()`, `getCreatedAt()`. `equals`/`hashCode` on `accountNumber`.
  - `Transaction(long id, long accountNumber, TransactionType type, BigDecimal amount, BigDecimal balanceAfter, LocalDateTime timestamp)` with matching getters `getId()`, `getAccountNumber()`, `getType()`, `getAmount()`, `getBalanceAfter()`, `getTimestamp()`.

- [ ] **Step 1: Write the failing test**

`src/test/java/com/bank/model/AccountTest.java`:

```java
package com.bank.model;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import static org.junit.jupiter.api.Assertions.*;

class AccountTest {
    @Test
    void holdsValuesAndComparesByAccountNumber() {
        LocalDateTime now = LocalDateTime.now();
        Account a = new Account(1234567890L, "Asha", "1234",
                new BigDecimal("100.00"), AccountType.SAVINGS, AccountStatus.ACTIVE, now);

        assertEquals(1234567890L, a.getAccountNumber());
        assertEquals("Asha", a.getHolderName());
        assertEquals(new BigDecimal("100.00"), a.getBalance());
        assertEquals(AccountType.SAVINGS, a.getAccountType());
        assertEquals(AccountStatus.ACTIVE, a.getStatus());

        Account same = new Account(1234567890L, "Different", "9999",
                BigDecimal.ZERO, AccountType.CURRENT, AccountStatus.BLOCKED, now);
        assertEquals(a, same);
        assertEquals(a.hashCode(), same.hashCode());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q -Dtest=AccountTest test`
Expected: FAIL — cannot find symbol `Account`.

- [ ] **Step 3: Write the POJOs**

`src/main/java/com/bank/model/Account.java`:

```java
package com.bank.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;

public class Account {
    private final long accountNumber;
    private final String holderName;
    private final String pin;
    private final BigDecimal balance;
    private final AccountType accountType;
    private final AccountStatus status;
    private final LocalDateTime createdAt;

    public Account(long accountNumber, String holderName, String pin, BigDecimal balance,
                   AccountType accountType, AccountStatus status, LocalDateTime createdAt) {
        this.accountNumber = accountNumber;
        this.holderName = holderName;
        this.pin = pin;
        this.balance = balance;
        this.accountType = accountType;
        this.status = status;
        this.createdAt = createdAt;
    }

    public long getAccountNumber() { return accountNumber; }
    public String getHolderName() { return holderName; }
    public String getPin() { return pin; }
    public BigDecimal getBalance() { return balance; }
    public AccountType getAccountType() { return accountType; }
    public AccountStatus getStatus() { return status; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Account)) return false;
        return accountNumber == ((Account) o).accountNumber;
    }

    @Override
    public int hashCode() { return Objects.hash(accountNumber); }

    @Override
    public String toString() {
        return "Account{accountNumber=" + accountNumber + ", holderName='" + holderName
                + "', balance=" + balance + ", accountType=" + accountType
                + ", status=" + status + "}";
    }
}
```

`src/main/java/com/bank/model/Transaction.java`:

```java
package com.bank.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class Transaction {
    private final long id;
    private final long accountNumber;
    private final TransactionType type;
    private final BigDecimal amount;
    private final BigDecimal balanceAfter;
    private final LocalDateTime timestamp;

    public Transaction(long id, long accountNumber, TransactionType type,
                       BigDecimal amount, BigDecimal balanceAfter, LocalDateTime timestamp) {
        this.id = id;
        this.accountNumber = accountNumber;
        this.type = type;
        this.amount = amount;
        this.balanceAfter = balanceAfter;
        this.timestamp = timestamp;
    }

    public long getId() { return id; }
    public long getAccountNumber() { return accountNumber; }
    public TransactionType getType() { return type; }
    public BigDecimal getAmount() { return amount; }
    public BigDecimal getBalanceAfter() { return balanceAfter; }
    public LocalDateTime getTimestamp() { return timestamp; }

    @Override
    public String toString() {
        return "Transaction{id=" + id + ", accountNumber=" + accountNumber
                + ", type=" + type + ", amount=" + amount
                + ", balanceAfter=" + balanceAfter + ", timestamp=" + timestamp + "}";
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -q -Dtest=AccountTest test`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/bank/model/Account.java src/main/java/com/bank/model/Transaction.java src/test/java/com/bank/model/AccountTest.java
git commit -m "feat(model): add Account and Transaction POJOs"
```

---

## Task 4: DaoException + Database + schema.sql + SchemaInitializer

**Files:**
- Create: `src/main/java/com/bank/dao/DaoException.java`
- Create: `src/main/java/com/bank/db/Database.java`
- Create: `src/main/resources/schema.sql`
- Create: `src/main/java/com/bank/db/SchemaInitializer.java`
- Test: `src/test/java/com/bank/db/SchemaInitializerTest.java`

**Interfaces:**
- Consumes: nothing (uses JDBC + resources).
- Produces:
  - `DaoException extends RuntimeException` with `DaoException(String, Throwable)` and `DaoException(String)`.
  - `Database` with `static Database fromResource(String resourceName)`, `static Database defaultInstance()`, `Connection getConnection()`.
  - `SchemaInitializer` with `static void initialize(Database db)`.

- [ ] **Step 1: Write the failing test**

`src/test/java/com/bank/db/SchemaInitializerTest.java`:

```java
package com.bank.db;

import com.bank.dao.DaoException;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

class SchemaInitializerTest {

    private final Database db = Database.fromResource("db.test.properties");

    @Test
    void initializeCreatesAllThreeTables() throws Exception {
        SchemaInitializer.initialize(db);   // idempotent — safe to run repeatedly

        try (Connection c = db.getConnection(); Statement st = c.createStatement()) {
            for (String table : new String[]{"accounts", "transactions", "admins"}) {
                try (ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM " + table)) {
                    assertTrue(rs.next(), "table missing: " + table);
                }
            }
        }
    }

    @Test
    void getConnectionSucceeds() throws Exception {
        try (Connection c = db.getConnection()) {
            assertTrue(c.isValid(2));
        }
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q -Dtest=SchemaInitializerTest test`
Expected: FAIL — cannot find symbol `Database` / `SchemaInitializer`.

- [ ] **Step 3: Write `DaoException`**

`src/main/java/com/bank/dao/DaoException.java`:

```java
package com.bank.dao;

public class DaoException extends RuntimeException {
    public DaoException(String message) { super(message); }
    public DaoException(String message, Throwable cause) { super(message, cause); }
}
```

- [ ] **Step 4: Write `Database`**

`src/main/java/com/bank/db/Database.java`:

```java
package com.bank.db;

import com.bank.dao.DaoException;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

public class Database {
    private final String url;
    private final String user;
    private final String password;

    public Database(Properties props) {
        this.url = props.getProperty("db.url");
        this.user = props.getProperty("db.user");
        this.password = props.getProperty("db.password", "");
    }

    public static Database fromResource(String resourceName) {
        Properties props = new Properties();
        try (InputStream in = Database.class.getClassLoader().getResourceAsStream(resourceName)) {
            if (in == null) {
                throw new IllegalStateException("Config resource not found: " + resourceName);
            }
            props.load(in);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read " + resourceName, e);
        }
        return new Database(props);
    }

    public static Database defaultInstance() {
        return fromResource("db.properties");
    }

    public Connection getConnection() {
        try {
            return DriverManager.getConnection(url, user, password);
        } catch (SQLException e) {
            throw new DaoException("Failed to open connection to " + url, e);
        }
    }
}
```

- [ ] **Step 5: Write `schema.sql`**

`src/main/resources/schema.sql` (only `CREATE TABLE IF NOT EXISTS` — no `CREATE DATABASE`/`USE`, so it works against whichever database the connection URL selects):

```sql
CREATE TABLE IF NOT EXISTS accounts (
    account_number BIGINT PRIMARY KEY,
    holder_name    VARCHAR(100)   NOT NULL,
    pin            VARCHAR(64)    NOT NULL,
    balance        DECIMAL(15,2)  NOT NULL DEFAULT 0.00,
    account_type   VARCHAR(20)    NOT NULL,
    status         VARCHAR(20)    NOT NULL DEFAULT 'ACTIVE',
    created_at     TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS transactions (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    account_number BIGINT         NOT NULL,
    type           VARCHAR(20)    NOT NULL,
    amount         DECIMAL(15,2)  NOT NULL,
    balance_after  DECIMAL(15,2)  NOT NULL,
    timestamp      TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_tx_account FOREIGN KEY (account_number)
        REFERENCES accounts(account_number)
);

CREATE TABLE IF NOT EXISTS admins (
    id       INT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    password VARCHAR(64) NOT NULL
);
```

- [ ] **Step 6: Write `SchemaInitializer`**

`src/main/java/com/bank/db/SchemaInitializer.java`:

```java
package com.bank.db;

import com.bank.dao.DaoException;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public final class SchemaInitializer {

    private SchemaInitializer() { }

    public static void initialize(Database db) {
        String script = readScript("schema.sql");
        try (Connection c = db.getConnection(); Statement st = c.createStatement()) {
            for (String statement : script.split(";")) {
                if (!statement.isBlank()) {
                    st.execute(statement);
                }
            }
        } catch (SQLException e) {
            throw new DaoException("Schema initialization failed", e);
        }
    }

    private static String readScript(String resourceName) {
        try (InputStream in = SchemaInitializer.class.getClassLoader()
                .getResourceAsStream(resourceName)) {
            if (in == null) {
                throw new IllegalStateException("Script not found: " + resourceName);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read " + resourceName, e);
        }
    }
}
```

- [ ] **Step 7: Run test to verify it passes**

Run: `mvn -q -Dtest=SchemaInitializerTest test`
Expected: PASS (requires `bank_test` database to exist — see Prerequisite).

- [ ] **Step 8: Commit**

```bash
git add src/main/java/com/bank/dao/DaoException.java src/main/java/com/bank/db src/main/resources/schema.sql src/test/java/com/bank/db/SchemaInitializerTest.java
git commit -m "feat(db): add Database, schema.sql, and SchemaInitializer"
```

---

## Task 5: AccountDao + AccountDaoJdbc

**Files:**
- Create: `src/main/java/com/bank/dao/AccountDao.java`
- Create: `src/main/java/com/bank/dao/AccountDaoJdbc.java`
- Test: `src/test/java/com/bank/dao/AccountDaoJdbcTest.java`

**Interfaces:**
- Consumes: `Account`, `AccountType`, `AccountStatus` (Tasks 2–3); `Database`, `DaoException`, `SchemaInitializer` (Task 4).
- Produces `AccountDao`:
  - `void create(Account account)`
  - `Optional<Account> findByAccountNumber(long accountNumber)`
  - `List<Account> findAll()`
  - `void updateBalance(long accountNumber, BigDecimal newBalance)`
  - `void updatePin(long accountNumber, String newPin)`
  - `void updateStatus(long accountNumber, AccountStatus status)`
  - Implementation `AccountDaoJdbc(Database db)`.

- [ ] **Step 1: Write the failing test**

`src/test/java/com/bank/dao/AccountDaoJdbcTest.java`:

```java
package com.bank.dao;

import com.bank.db.Database;
import com.bank.db.SchemaInitializer;
import com.bank.model.Account;
import com.bank.model.AccountStatus;
import com.bank.model.AccountType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class AccountDaoJdbcTest {

    private static final Database db = Database.fromResource("db.test.properties");
    private final AccountDao dao = new AccountDaoJdbc(db);

    @BeforeAll
    static void createSchema() {
        SchemaInitializer.initialize(db);
    }

    @BeforeEach
    void cleanTables() throws Exception {
        try (Connection c = db.getConnection(); Statement st = c.createStatement()) {
            st.execute("DELETE FROM transactions");
            st.execute("DELETE FROM accounts");
        }
    }

    private Account sample(long number) {
        return new Account(number, "Asha", "1234", new BigDecimal("100.00"),
                AccountType.SAVINGS, AccountStatus.ACTIVE, LocalDateTime.now());
    }

    @Test
    void createThenFindRoundTrips() {
        dao.create(sample(1000000001L));

        Optional<Account> found = dao.findByAccountNumber(1000000001L);
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
        assertTrue(dao.findByAccountNumber(9999999999L).isEmpty());
    }

    @Test
    void findAllReturnsEveryAccount() {
        dao.create(sample(1000000001L));
        dao.create(sample(1000000002L));
        List<Account> all = dao.findAll();
        assertEquals(2, all.size());
    }

    @Test
    void updateBalancePersistsExactValue() {
        dao.create(sample(1000000001L));
        dao.updateBalance(1000000001L, new BigDecimal("250.75"));
        assertEquals(0, new BigDecimal("250.75")
                .compareTo(dao.findByAccountNumber(1000000001L).orElseThrow().getBalance()));
    }

    @Test
    void updatePinPersists() {
        dao.create(sample(1000000001L));
        dao.updatePin(1000000001L, "4321");
        assertEquals("4321", dao.findByAccountNumber(1000000001L).orElseThrow().getPin());
    }

    @Test
    void updateStatusPersists() {
        dao.create(sample(1000000001L));
        dao.updateStatus(1000000001L, AccountStatus.BLOCKED);
        assertEquals(AccountStatus.BLOCKED,
                dao.findByAccountNumber(1000000001L).orElseThrow().getStatus());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q -Dtest=AccountDaoJdbcTest test`
Expected: FAIL — cannot find symbol `AccountDao` / `AccountDaoJdbc`.

- [ ] **Step 3: Write the `AccountDao` interface**

`src/main/java/com/bank/dao/AccountDao.java`:

```java
package com.bank.dao;

import com.bank.model.Account;
import com.bank.model.AccountStatus;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface AccountDao {
    void create(Account account);
    Optional<Account> findByAccountNumber(long accountNumber);
    List<Account> findAll();
    void updateBalance(long accountNumber, BigDecimal newBalance);
    void updatePin(long accountNumber, String newPin);
    void updateStatus(long accountNumber, AccountStatus status);
}
```

- [ ] **Step 4: Write the `AccountDaoJdbc` implementation**

`src/main/java/com/bank/dao/AccountDaoJdbc.java`:

```java
package com.bank.dao;

import com.bank.db.Database;
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

    private final Database db;

    public AccountDaoJdbc(Database db) {
        this.db = db;
    }

    @Override
    public void create(Account account) {
        String sql = "INSERT INTO accounts "
                + "(account_number, holder_name, pin, balance, account_type, status) "
                + "VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
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
    public Optional<Account> findByAccountNumber(long accountNumber) {
        String sql = "SELECT * FROM accounts WHERE account_number = ?";
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, accountNumber);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapRow(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new DaoException("findByAccountNumber failed", e);
        }
    }

    @Override
    public List<Account> findAll() {
        String sql = "SELECT * FROM accounts ORDER BY account_number";
        List<Account> result = new ArrayList<>();
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
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
    public void updateBalance(long accountNumber, BigDecimal newBalance) {
        runUpdate("UPDATE accounts SET balance = ? WHERE account_number = ?",
                ps -> {
                    ps.setBigDecimal(1, newBalance);
                    ps.setLong(2, accountNumber);
                }, "updateBalance");
    }

    @Override
    public void updatePin(long accountNumber, String newPin) {
        runUpdate("UPDATE accounts SET pin = ? WHERE account_number = ?",
                ps -> {
                    ps.setString(1, newPin);
                    ps.setLong(2, accountNumber);
                }, "updatePin");
    }

    @Override
    public void updateStatus(long accountNumber, AccountStatus status) {
        runUpdate("UPDATE accounts SET status = ? WHERE account_number = ?",
                ps -> {
                    ps.setString(1, status.name());
                    ps.setLong(2, accountNumber);
                }, "updateStatus");
    }

    private void runUpdate(String sql, StatementBinder binder, String label) {
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
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
Expected: PASS (all 6 tests green).

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/bank/dao/AccountDao.java src/main/java/com/bank/dao/AccountDaoJdbc.java src/test/java/com/bank/dao/AccountDaoJdbcTest.java
git commit -m "feat(dao): add AccountDao with JDBC implementation and tests"
```

---

## Task 6: TransactionDao + TransactionDaoJdbc

**Files:**
- Create: `src/main/java/com/bank/dao/TransactionDao.java`
- Create: `src/main/java/com/bank/dao/TransactionDaoJdbc.java`
- Test: `src/test/java/com/bank/dao/TransactionDaoJdbcTest.java`

**Interfaces:**
- Consumes: `Transaction`, `TransactionType` (Tasks 2–3); `Account`, `AccountType`, `AccountStatus` (for test setup — a transaction needs a parent account due to the FK); `Database`, `DaoException`, `SchemaInitializer`, `AccountDaoJdbc` (Tasks 4–5).
- Produces `TransactionDao`:
  - `void insert(Transaction transaction)` — assigns the DB-generated id; returns a new `Transaction` is NOT used — instead the generated id is available via a follow-up `findByAccountNumber`. (Note: `Transaction` is immutable, so `insert` does not mutate the passed object; tests read the id back via `findByAccountNumber`.)
  - `List<Transaction> findByAccountNumber(long accountNumber)`
  - `List<Transaction> findRecent(long accountNumber, int limit)`
  - Implementation `TransactionDaoJdbc(Database db)`.

- [ ] **Step 1: Write the failing test**

`src/test/java/com/bank/dao/TransactionDaoJdbcTest.java`:

```java
package com.bank.dao;

import com.bank.db.Database;
import com.bank.db.SchemaInitializer;
import com.bank.model.Account;
import com.bank.model.AccountStatus;
import com.bank.model.AccountType;
import com.bank.model.Transaction;
import com.bank.model.TransactionType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TransactionDaoJdbcTest {

    private static final Database db = Database.fromResource("db.test.properties");
    private final AccountDao accountDao = new AccountDaoJdbc(db);
    private final TransactionDao txDao = new TransactionDaoJdbc(db);

    private static final long ACC = 1000000001L;

    @BeforeAll
    static void createSchema() {
        SchemaInitializer.initialize(db);
    }

    @BeforeEach
    void cleanAndSeedAccount() throws Exception {
        try (Connection c = db.getConnection(); Statement st = c.createStatement()) {
            st.execute("DELETE FROM transactions");
            st.execute("DELETE FROM accounts");
        }
        accountDao.create(new Account(ACC, "Asha", "1234", new BigDecimal("100.00"),
                AccountType.SAVINGS, AccountStatus.ACTIVE, LocalDateTime.now()));
    }

    private Transaction tx(TransactionType type, String amount, String balanceAfter) {
        return new Transaction(0L, ACC, type, new BigDecimal(amount),
                new BigDecimal(balanceAfter), LocalDateTime.now());
    }

    @Test
    void insertAssignsGeneratedId() {
        txDao.insert(tx(TransactionType.DEPOSIT, "50.00", "150.00"));
        List<Transaction> found = txDao.findByAccountNumber(ACC);
        assertEquals(1, found.size());
        assertTrue(found.get(0).getId() > 0);
        assertEquals(TransactionType.DEPOSIT, found.get(0).getType());
        assertEquals(0, new BigDecimal("50.00").compareTo(found.get(0).getAmount()));
    }

    @Test
    void findByAccountNumberReturnsAllForAccount() {
        txDao.insert(tx(TransactionType.DEPOSIT, "50.00", "150.00"));
        txDao.insert(tx(TransactionType.WITHDRAW, "20.00", "130.00"));
        assertEquals(2, txDao.findByAccountNumber(ACC).size());
    }

    @Test
    void findRecentRespectsLimitAndNewestFirst() {
        txDao.insert(tx(TransactionType.DEPOSIT, "10.00", "110.00"));
        txDao.insert(tx(TransactionType.DEPOSIT, "20.00", "130.00"));
        txDao.insert(tx(TransactionType.DEPOSIT, "30.00", "160.00"));

        List<Transaction> recent = txDao.findRecent(ACC, 2);
        assertEquals(2, recent.size());
        // newest first: the last inserted (id largest) comes first
        assertTrue(recent.get(0).getId() > recent.get(1).getId());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q -Dtest=TransactionDaoJdbcTest test`
Expected: FAIL — cannot find symbol `TransactionDao` / `TransactionDaoJdbc`.

- [ ] **Step 3: Write the `TransactionDao` interface**

`src/main/java/com/bank/dao/TransactionDao.java`:

```java
package com.bank.dao;

import com.bank.model.Transaction;

import java.util.List;

public interface TransactionDao {
    void insert(Transaction transaction);
    List<Transaction> findByAccountNumber(long accountNumber);
    List<Transaction> findRecent(long accountNumber, int limit);
}
```

- [ ] **Step 4: Write the `TransactionDaoJdbc` implementation**

`src/main/java/com/bank/dao/TransactionDaoJdbc.java`:

```java
package com.bank.dao;

import com.bank.db.Database;
import com.bank.model.Transaction;
import com.bank.model.TransactionType;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class TransactionDaoJdbc implements TransactionDao {

    private final Database db;

    public TransactionDaoJdbc(Database db) {
        this.db = db;
    }

    @Override
    public void insert(Transaction transaction) {
        String sql = "INSERT INTO transactions "
                + "(account_number, type, amount, balance_after) VALUES (?, ?, ?, ?)";
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
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
    public List<Transaction> findByAccountNumber(long accountNumber) {
        String sql = "SELECT * FROM transactions WHERE account_number = ? ORDER BY id DESC";
        return query(sql, accountNumber, null);
    }

    @Override
    public List<Transaction> findRecent(long accountNumber, int limit) {
        String sql = "SELECT * FROM transactions WHERE account_number = ? "
                + "ORDER BY id DESC LIMIT ?";
        return query(sql, accountNumber, limit);
    }

    private List<Transaction> query(String sql, long accountNumber, Integer limit) {
        List<Transaction> result = new ArrayList<>();
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
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
Expected: PASS (all 3 tests green).

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/bank/dao/TransactionDao.java src/main/java/com/bank/dao/TransactionDaoJdbc.java src/test/java/com/bank/dao/TransactionDaoJdbcTest.java
git commit -m "feat(dao): add TransactionDao with JDBC implementation and tests"
```

---

## Task 7: Full suite + README note

**Files:**
- Create: `README.md`

**Interfaces:**
- Consumes: everything above.
- Produces: green full test suite + setup documentation.

- [ ] **Step 1: Run the whole suite**

Run: `mvn -q test`
Expected: BUILD SUCCESS — all tests across `model`, `db`, and `dao` pass.

- [ ] **Step 2: Write `README.md`**

`README.md`:

```markdown
# Bank Management System & ATM Simulator

Java + MySQL + JavaFX banking / ATM simulator, built in stages.
**Stage 1 (current):** database foundation — schema, connection, model, DAO layer.

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
mvn test        # run the DAO/DB test suite against bank_test
```

## Layout
- `com.bank.model` — Account, Transaction, enums (data carriers)
- `com.bank.db`    — Database (config/connection), SchemaInitializer
- `com.bank.dao`   — AccountDao / TransactionDao interfaces + JDBC implementations
```

- [ ] **Step 3: Commit**

```bash
git add README.md
git commit -m "docs: add README with Stage 1 setup instructions"
```

---

## Self-Review Notes

- **Spec coverage:** §3 pom → Task 1; §4 schema → Task 4; §5 connection/config → Task 4; §6 models → Tasks 2–3; §7 DAO (all listed methods) → Tasks 5–6; §8 testing (bank_test, TDD, coverage list) → Tasks 5–6; §9 success criteria → satisfied by Task 7 full-suite run. All covered.
- **Placeholder scan:** no TBD/TODO; all code shown in full.
- **Type consistency:** `Database.fromResource`, `Database.getConnection`, `SchemaInitializer.initialize`, DAO method signatures, and enum names are identical everywhere they appear across tasks and match the spec §7.
- **Note on immutability:** `Transaction`/`Account` are immutable; `insert` does not mutate the passed object — tests read generated ids back via `findByAccountNumber` (documented in Task 6 interfaces).
```

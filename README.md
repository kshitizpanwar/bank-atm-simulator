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

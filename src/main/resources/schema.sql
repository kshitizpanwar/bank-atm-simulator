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
    `timestamp`    TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_tx_account FOREIGN KEY (account_number)
        REFERENCES accounts(account_number)
);

CREATE TABLE IF NOT EXISTS admins (
    id       INT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    password VARCHAR(64) NOT NULL
);

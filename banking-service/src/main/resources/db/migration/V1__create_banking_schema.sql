-- Banking Service Schema
-- Version: 1
-- Description: Create banking schema and core tables

CREATE SCHEMA IF NOT EXISTS banking;

-- Accounts table
CREATE TABLE banking.accounts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    account_number VARCHAR(20) NOT NULL UNIQUE,
    account_type VARCHAR(20) NOT NULL CHECK (account_type IN ('SAVINGS', 'CHECKING')),
    balance DECIMAL(19,4) NOT NULL DEFAULT 0.0000 CHECK (balance >= 0),
    currency VARCHAR(3) NOT NULL DEFAULT 'USD',
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'FROZEN', 'CLOSED')),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0
);

-- Transactions table
CREATE TABLE banking.transactions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    account_id UUID NOT NULL REFERENCES banking.accounts(id),
    transaction_type VARCHAR(20) NOT NULL CHECK (transaction_type IN ('DEPOSIT', 'WITHDRAWAL', 'TRANSFER_IN', 'TRANSFER_OUT')),
    amount DECIMAL(19,4) NOT NULL CHECK (amount > 0),
    currency VARCHAR(3) NOT NULL DEFAULT 'USD',
    status VARCHAR(20) NOT NULL CHECK (status IN ('PENDING', 'COMPLETED', 'FAILED', 'REVERSED')),
    description TEXT,
    reference_id UUID,
    metadata JSONB,
    idempotency_key VARCHAR(255) UNIQUE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    completed_at TIMESTAMP
);

-- Ledger entries table (double-entry accounting)
CREATE TABLE banking.ledger_entries (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    transaction_id UUID NOT NULL REFERENCES banking.transactions(id),
    account_id UUID NOT NULL REFERENCES banking.accounts(id),
    entry_type VARCHAR(6) NOT NULL CHECK (entry_type IN ('DEBIT', 'CREDIT')),
    amount DECIMAL(19,4) NOT NULL CHECK (amount > 0),
    balance_after DECIMAL(19,4) NOT NULL CHECK (balance_after >= 0),
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Indexes for performance
CREATE INDEX idx_accounts_user_id ON banking.accounts(user_id);
CREATE INDEX idx_accounts_account_number ON banking.accounts(account_number);
CREATE INDEX idx_accounts_status ON banking.accounts(status);
CREATE INDEX idx_transactions_account_id ON banking.transactions(account_id);
CREATE INDEX idx_transactions_created_at ON banking.transactions(created_at DESC);
CREATE INDEX idx_transactions_idempotency_key ON banking.transactions(idempotency_key);
CREATE INDEX idx_transactions_status ON banking.transactions(status);
CREATE INDEX idx_ledger_account_created ON banking.ledger_entries(account_id, created_at DESC);
CREATE INDEX idx_ledger_transaction ON banking.ledger_entries(transaction_id);

-- Function to check ledger balance integrity
CREATE OR REPLACE FUNCTION banking.check_ledger_balance() 
RETURNS TABLE(account_id UUID, ledger_balance DECIMAL, account_balance DECIMAL, difference DECIMAL) AS $$
BEGIN
    RETURN QUERY
    SELECT 
        a.id,
        COALESCE(SUM(CASE WHEN l.entry_type = 'CREDIT' THEN l.amount ELSE -l.amount END), 0) AS ledger_balance,
        a.balance AS account_balance,
        a.balance - COALESCE(SUM(CASE WHEN l.entry_type = 'CREDIT' THEN l.amount ELSE -l.amount END), 0) AS difference
    FROM banking.accounts a
    LEFT JOIN banking.ledger_entries l ON a.id = l.account_id
    GROUP BY a.id, a.balance
    HAVING ABS(a.balance - COALESCE(SUM(CASE WHEN l.entry_type = 'CREDIT' THEN l.amount ELSE -l.amount END), 0)) > 0.0001;
END;
$$ LANGUAGE plpgsql;

package com.banking.auth.exception;

public class AccountLockedException extends RuntimeException {

    public AccountLockedException() {
        super("Account is locked due to too many failed login attempts");
    }

    public AccountLockedException(String message) {
        super(message);
    }
}

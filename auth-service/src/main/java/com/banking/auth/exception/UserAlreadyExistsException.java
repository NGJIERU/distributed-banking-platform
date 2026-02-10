package com.banking.auth.exception;

public class UserAlreadyExistsException extends RuntimeException {

    public UserAlreadyExistsException(String message) {
        super(message);
    }

    public UserAlreadyExistsException(String email, Throwable cause) {
        super("User with email " + email + " already exists", cause);
    }
}

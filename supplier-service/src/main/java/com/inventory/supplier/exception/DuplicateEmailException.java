package com.inventory.supplier.exception;

public class DuplicateEmailException extends RuntimeException {
    public DuplicateEmailException(String email) {
        super("A supplier with email '" + email + "' already exists");
    }
}
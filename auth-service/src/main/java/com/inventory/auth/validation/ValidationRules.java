package com.inventory.auth.validation;

public final class ValidationRules {

    private ValidationRules() {
    }

    public static final int PASSWORD_MIN_LENGTH = 8;
    public static final int PASSWORD_MAX_LENGTH = 72;
    public static final String PASSWORD_LENGTH_MESSAGE = "Password must be 8-72 characters";
    public static final String STRONG_PASSWORD_REGEX = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@#$%^&+=!*]).+$";
    public static final String STRONG_PASSWORD_MESSAGE =
            "Password must contain at least one uppercase letter, one lowercase letter, one digit, and one special character (@#$%^&+=!*)";
}

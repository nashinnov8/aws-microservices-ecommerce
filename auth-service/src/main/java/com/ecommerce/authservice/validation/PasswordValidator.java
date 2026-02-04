package com.ecommerce.authservice.validation;

import com.ecommerce.authservice.exception.WeakPasswordException;
import org.springframework.stereotype.Component;

@Component
public class PasswordValidator {
    public void validate(String password) {
        if (password.length() < 8) {
            throw new WeakPasswordException("Password must be at least 8 characters");
        }
        if (!password.matches(".*[A-Z].*")) {
            throw new WeakPasswordException("Password must contain uppercase letter");
        }
        if (!password.matches(".*[0-9].*")) {
            throw new WeakPasswordException("Password must contain digit");
        }
        if (!password.matches(".*[@$!%*?&].*")) {
            throw new WeakPasswordException("Password must contain special character");
        }
    }
}

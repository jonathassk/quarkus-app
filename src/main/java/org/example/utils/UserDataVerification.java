package org.example.utils;

import org.example.application.dto.UserRequestDTO;

import java.util.LinkedList;
import java.util.List;

public class UserDataVerification {

    public List<String> verifyUserData(UserRequestDTO user) {
        List<String> listErrors;
        listErrors = verifyNullOrEmpty(user);
        List<String> dateOfBirthErrors = verifyDateOfBirth(user);
        if (!dateOfBirthErrors.isEmpty()) listErrors.addAll(dateOfBirthErrors);
        return listErrors;
    }

    private List<String> verifyNullOrEmpty(UserRequestDTO user) {
        List<String> errors = new LinkedList<>();
        if (user.fullname() == null || user.fullname().isEmpty()) errors.add("Full name is required");
        if (user.email() == null || user.email().isEmpty()) {
            errors.add("Email is required");
        } else {
            if (user.email().length() < 5) errors.add("Email must be at least 5 characters long");
        }
        if (user.password() == null || user.password().isEmpty()) {
            errors.add("Password is required");
        } else {
            if (user.password().length() < 8) errors.add("Password must be at least 8 characters long");
            if (!user.password().matches(".*[A-Z].*")) errors.add("Password must contain at least one uppercase letter");
        }
        if (user.username() == null || user.username().isEmpty()) {
            errors.add("Username is required");
        } else {
            if (user.username().length() < 4) errors.add("Username must be at least 4 characters long");
            if (!user.username().matches("^[a-zA-Z0-9._-]{3,}$")) errors.add("Username is invalid");
        }
        if (user.city() == null || user.city().isEmpty()) errors.add("City is required");
        if (user.country() == null || user.country().isEmpty()) errors.add("Country is required");
        return errors;
    }

    private List<String> verifyDateOfBirth(UserRequestDTO user) {
        List<String> errors = new LinkedList<>();
        if (user.dateOfBirth() == null || user.dateOfBirth().isEmpty()) {
            errors.add("Date of birth is required");
        } else {
            String[] dateParts = user.dateOfBirth().split("/");
            if (dateParts.length != 3) {
                errors.add("Date of birth format is invalid");
            } else {
                try {
                    int day = Integer.parseInt(dateParts[0]);
                    int month = Integer.parseInt(dateParts[1]);
                    int year = Integer.parseInt(dateParts[2]);
                    if (day < 1 || day > 31 || month < 1 || month > 12 || year < 1900) {
                        errors.add("Date of birth is invalid");
                    }
                } catch (NumberFormatException e) {
                    errors.add("Date of birth format is invalid");
                }
            }
        }
        return errors;
    }

}

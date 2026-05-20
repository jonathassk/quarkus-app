package org.example.utils;

import jakarta.json.bind.adapter.JsonbAdapter;
import org.example.domain.enums.Gender;

public class GenderJsonbAdapter implements JsonbAdapter<Gender, String> {
    @Override
    public String adaptToJson(Gender gender) {
        return gender == null ? null : gender.name().toLowerCase();
    }

    @Override
    public Gender adaptFromJson(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            return null;
        }
        return Gender.valueOf(normalized.toUpperCase());
    }
}


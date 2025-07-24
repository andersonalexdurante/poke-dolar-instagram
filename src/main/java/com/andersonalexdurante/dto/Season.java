package com.andersonalexdurante.dto;

import com.fasterxml.jackson.annotation.JsonValue;

public enum Season {
    SPRING("spring"),
    SUMMER("summer"),
    AUTUMN("autumn"),
    WINTER("winter");

    private final String value;

    Season(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    public static Season fromString(String text) {
        for (Season s : Season.values()) {
            if (s.value.equalsIgnoreCase(text)) {
                return s;
            }
        }
        throw new IllegalArgumentException("No constant with text " + text + " found");
    }
}
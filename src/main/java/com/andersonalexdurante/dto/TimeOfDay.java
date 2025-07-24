package com.andersonalexdurante.dto;

import com.fasterxml.jackson.annotation.JsonValue;

public enum TimeOfDay {
    DAWN("dawn"),
    MORNING("morning"),
    AFTERNOON("afternoon"),
    DUSK("dusk"),
    TWILIGHT("twilight"),
    EVENING("evening"),
    NIGHT("night");

    private final String value;

    TimeOfDay(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    public static TimeOfDay fromString(String text) {
        for (TimeOfDay tod : TimeOfDay.values()) {
            if (tod.value.equalsIgnoreCase(text)) {
                return tod;
            }
        }
        throw new IllegalArgumentException("No constant with text " + text + " found");
    }
}
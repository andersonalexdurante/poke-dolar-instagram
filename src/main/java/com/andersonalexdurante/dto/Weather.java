package com.andersonalexdurante.dto;

import com.fasterxml.jackson.annotation.JsonValue;

public enum Weather {
    CLEAR_SKY("clear sky"),
    OVERCAST("overcast"),
    SUNNY("sunny"),
    FOGGY("foggy"),
    RAIN("rain"),
    THUNDERSTORMS("thunderstorms"),
    SNOWFALL("snowfall"),
    STRONG_WIND("strong wind");

    private final String value;

    Weather(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    public static Weather fromString(String text) {
        for (Weather w : Weather.values()) {
            if (w.value.equalsIgnoreCase(text)) {
                return w;
            }
        }
        throw new IllegalArgumentException("No constant with text " + text + " found");
    }
}
package com.andersonalexdurante.dto;

import com.fasterxml.jackson.annotation.JsonValue;

public enum Weather {
    CLEAR_SKY("clear sky"),
    PARTLY_CLOUDY("partly cloudy"),
    OVERCAST("overcast"),
    MISTY("misty"),
    FOGGY("foggy"),
    LIGHT_DRIZZLE("light drizzle"),
    HEAVY_RAIN("heavy rain"),
    THUNDERSTORMS("thunderstorms"),
    HEAVY_SNOWFALL("heavy snowfall"),
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
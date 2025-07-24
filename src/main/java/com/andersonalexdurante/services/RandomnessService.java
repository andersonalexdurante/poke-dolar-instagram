package com.andersonalexdurante.services;

import com.andersonalexdurante.dto.*;
import jakarta.enterprise.context.ApplicationScoped;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.SecureRandom;

@ApplicationScoped
public class RandomnessService {
    private static final Logger LOGGER = LoggerFactory.getLogger(RandomnessService.class);
    private final SecureRandom secureRandom = new SecureRandom();

    public RandomSelection getRandomOptions(String requestId) {
        TimeOfDay randomTimeOfDay = getRandomEnum(TimeOfDay.values());
        Weather randomWeather = getRandomEnum(Weather.values());
        Season randomSeason = getRandomEnum(Season.values());

        RandomSelection selection = new RandomSelection(
                randomTimeOfDay,
                randomWeather,
                randomSeason
        );

        LOGGER.info("[{}] Selected random time: {}, weather: {}, season: {}",
                requestId,
                selection.timeOfDay().getValue(),
                selection.weather().getValue(),
                selection.season().getValue());

        return selection;
    }

    private <T> T getRandomEnum(T[] values) {
        if (values == null || values.length == 0) {
            return null;
        }
        return values[secureRandom.nextInt(values.length)];
    }
}

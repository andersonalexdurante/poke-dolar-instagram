package com.andersonalexdurante.services;

import com.andersonalexdurante.dto.RandomSelection;
import com.andersonalexdurante.dto.RandomnessOptions;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.security.SecureRandom;
import java.util.List;
import java.util.Random;

@ApplicationScoped
public class RandomnessService {
    private static final Logger LOGGER = LoggerFactory.getLogger(RandomnessService.class);

    private final ObjectMapper objectMapper;
    private final Random random;
    private RandomnessOptions randomnessOptions;

    @Inject
    public RandomnessService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.random = new Random();
    }

    @PostConstruct
    void init() {
        loadRandomnessOptions();
    }

    private void loadRandomnessOptions() {
        try (InputStream inputStream = Thread.currentThread()
                .getContextClassLoader()
                .getResourceAsStream("randomness.json")) {

            if (inputStream == null) {
                throw new RuntimeException("randomness.json not found in resources");
            }

            this.randomnessOptions = this.objectMapper.readValue(inputStream, RandomnessOptions.class);
        } catch (IOException e) {
            LOGGER.error("Failed to load randomness configuration: {}", e.getMessage());
            throw new RuntimeException("Failed to load randomness configuration", e);
        }
    }

    public RandomSelection getRandomOptions(String requestId) {
        RandomSelection selection = new RandomSelection(
                getRandomElement(randomnessOptions.time_of_day()),
                getRandomElement(randomnessOptions.weather()),
                getRandomElement(randomnessOptions.season())
        );

        LOGGER.info("[{}] Selected random time: {}, weather: {}, season: {}",
                requestId,
                selection.timeOfDay(),
                selection.weather(),
                selection.season());

        return selection;
    }

    private <T> T getRandomElement(List<T> list) {
        if (list == null || list.isEmpty()) {
            return null;
        }
        SecureRandom secureRandom = new SecureRandom();
        return list.get(secureRandom.nextInt(list.size()));
    }

}
package com.andersonalexdurante.services;

import com.andersonalexdurante.dto.PokemonDTO;
import com.andersonalexdurante.exceptions.VideoException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.http.SdkHttpClient;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.lambda.LambdaClient;
import software.amazon.awssdk.services.lambda.model.InvokeRequest;
import software.amazon.awssdk.services.lambda.model.InvokeResponse;

import java.io.*;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@ApplicationScoped
public class VideoService {

    private static final Logger LOGGER = LoggerFactory.getLogger(VideoService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    @ConfigProperty(name = "IMAGE_GENERATOR_LAMBDA")
    String imageGeneratorLambda;

    public void generatePostVideo(String requestId, String dollarExchangeRate, boolean dollarup,
                                  PokemonDTO newPokemon, String backgroundImageDescription) {
        LOGGER.info("[{}] Starting video generation for Pokemon #{} - {}",
                requestId, newPokemon.number(), newPokemon.name());

        SdkHttpClient urlClient = UrlConnectionHttpClient.builder()
                .connectionTimeout(Duration.ofSeconds(10))      // TCP connect
                .socketTimeout(Duration.ofMinutes(5))           // wait for data
                .build();

        ClientOverrideConfiguration overrideConfig = ClientOverrideConfiguration.builder()
                .apiCallTimeout(Duration.ofMinutes(6)) // Total API call timeout
                .apiCallAttemptTimeout(Duration.ofMinutes(6)) // Timeout for individual attempts
                .build();

        try (LambdaClient lambdaClient = LambdaClient.builder()
                .httpClient(urlClient)
                .region(Region.US_EAST_2)
                .overrideConfiguration(overrideConfig)
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build()) {

            Map<String, Object> payloadMap = new HashMap<>();
            payloadMap.put("dollar_rate", dollarExchangeRate);
            payloadMap.put("dollar_up", dollarup);
            payloadMap.put("pokedex_number", newPokemon.number());
            payloadMap.put("pokemon_name", newPokemon.name());
            payloadMap.put("background_description", backgroundImageDescription);

            String jsonPayload = this.objectMapper.writeValueAsString(payloadMap);
            LOGGER.debug("[{}] JSON payload created: {}", requestId, jsonPayload.length());

            LOGGER.info("[{}] Invoking Lambda function: {}", requestId, this.imageGeneratorLambda);
            InvokeRequest request = InvokeRequest.builder()
                    .functionName(this.imageGeneratorLambda)
                    .payload(SdkBytes.fromUtf8String(jsonPayload))
                    .build();

            InvokeResponse response = lambdaClient.invoke(request);
            String responseJson = response.payload().asUtf8String();
            LOGGER.debug("[{}] Lambda response received: {}", requestId, responseJson.length());

            Map  responseMap = this.objectMapper.readValue(responseJson, Map.class);
            Integer statusCode = (Integer) responseMap.get("statusCode");
            if (statusCode == null || statusCode != 200) {
                LOGGER.error("[{}] Python lambda returned error for generating video: {}", requestId, statusCode);
                throw new VideoException("Python lambda returned error for generating video: " + statusCode);
            }

            LOGGER.info("[{}] Video generated successfully!", requestId);
        } catch (IOException e) {
            LOGGER.error("[{}] Failed to generate video!", requestId, e);
            throw new RuntimeException("Failed to generate video", e);
        } catch (Exception e) {
            LOGGER.error("[{}] Unexpected error during video generation!", requestId, e);
            throw new VideoException("Unexpected error during video generation", e);
        }
    }
}


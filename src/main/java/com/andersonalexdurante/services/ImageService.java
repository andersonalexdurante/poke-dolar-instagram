package com.andersonalexdurante.services;

import com.andersonalexdurante.dto.PokemonDTO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.lambda.LambdaClient;
import software.amazon.awssdk.services.lambda.model.InvokeRequest;
import software.amazon.awssdk.services.lambda.model.InvokeResponse;

import java.io.*;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@ApplicationScoped
public class ImageService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ImageService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    @ConfigProperty(name = "IMAGE_GENERATOR_LAMBDA")
    String imageGeneratorLambda;
    @ConfigProperty(name = "SPECIAL_IMAGE_INITIAL_PERCENTAGE_CHANCE")
    String specialImageInitialPercentageChance;
    @ConfigProperty(name = "SPECIAL_IMAGE_FINAL_EVOLUTION_PERCENTAGE_CHANCE")
    String specialImageFinalEvolutionPercentageChance;
    @ConfigProperty(name = "SPECIAL_IMAGE_DOLLAR_CENTS_VARIATION")
    String specialImageDollarCentsVariation;
    @ConfigProperty(name = "SPECIAL_IMAGE_DOLLAR_CENTS_VARIATION_PERCENTAGE_CHANCE")
    String specialImageDollarCentsVariationPercentageChance;

    @Inject
    DynamoDBService dynamoDBService;

    public boolean shouldGenerateSpecialImage(String requestId, String pokemonName, boolean isFinalEvolution,
                                              double dollarVariation) {
        double chance = Double.parseDouble(this.specialImageInitialPercentageChance);
        LOGGER.debug("[{}] Initial chance set to: {}%", requestId, chance);

        List<Map<String, AttributeValue>> recentSpecialImagePosts = this.dynamoDBService.getRecentSpecialImagePosts(pokemonName);
        if (!recentSpecialImagePosts.isEmpty()) {
            LOGGER.info("[{}] Recent special image found for {}. Gradient Background will be generated.", requestId,
                    pokemonName);
            return false;
        }

        if (isFinalEvolution) {
            chance += Double.parseDouble(this.specialImageFinalEvolutionPercentageChance);
            LOGGER.info("[{}] Pokemon is final evolution. Chance increased to: {}%", requestId, chance);
        }

        if (dollarVariation >= Double.parseDouble(this.specialImageDollarCentsVariation)) {
            chance += Double.parseDouble(this.specialImageDollarCentsVariationPercentageChance);
            LOGGER.info("[{}] Significant dollar variation detected. Chance increased to: {}%", requestId, chance);
        }

        boolean shouldGenerate = Math.random() * 100 < chance;
        LOGGER.info("[{}] Special image generation decision for {}: {} (Final chance: {}%)",
                requestId, pokemonName, shouldGenerate, chance);

        return shouldGenerate;
    }


    public InputStream editPokemonImage(String requestId, String dollarExchangeRate, boolean dollarup,
                                        PokemonDTO newPokemon, InputStream pokemonImageStream, boolean specialImage,
                                        String backgroundImageDescription) {
        LOGGER.info("[{}] Starting image generation for Pokemon #{} - {}",
                requestId, newPokemon.number(), newPokemon.name());

        try (LambdaClient lambdaClient = LambdaClient.builder()
                .region(Region.US_EAST_2)
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build()) {

            byte[] imageBytes = pokemonImageStream.readAllBytes();
            String base64Image = Base64.getEncoder().encodeToString(imageBytes);
            LOGGER.debug("[{}] Image converted to Base64 ({} bytes)", requestId, imageBytes.length);

            Map<String, Object> payloadMap = new HashMap<>();
            payloadMap.put("image", base64Image);
            payloadMap.put("dollar_rate", dollarExchangeRate);
            payloadMap.put("dollar_up", dollarup);
            payloadMap.put("pokedex_number", newPokemon.number());
            payloadMap.put("pokemon_name", newPokemon.name());
            payloadMap.put("special_image", specialImage);
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

            String base64OutputImage = extractBase64Image(responseJson);
            if (base64OutputImage == null) {
                LOGGER.error("[{}] Invalid Lambda response: {}", requestId, responseJson.length());
                throw new IOException("Invalid Lambda response");
            }

            byte[] outputBytes = Base64.getDecoder().decode(base64OutputImage);
            LOGGER.info("[{}] Image successfully generated by Lambda", requestId);

            return new ByteArrayInputStream(outputBytes);
        } catch (IOException e) {
            LOGGER.error("[{}] Failed to process image!", requestId, e);
            throw new RuntimeException("Failed to generate image", e);
        }
    }

    private String extractBase64Image(String jsonResponse) {
        try {
            JsonNode rootNode = objectMapper.readTree(jsonResponse);
            JsonNode bodyNode = rootNode.path("body");

            if (bodyNode.isTextual()) {
                bodyNode = objectMapper.readTree(bodyNode.asText());
            }

            String base64Image = bodyNode.path("image").asText(null);

            if (base64Image == null || base64Image.isEmpty()) {
                LOGGER.error("Failed to extract 'image' from body: {}", bodyNode);
                return null;
            }

            LOGGER.debug("Successfully extracted Base64 image.");
            return base64Image;

        } catch (Exception e) {
            LOGGER.error("Error extracting Base64 image from JSON response: {}", jsonResponse, e);
            return null;
        }
    }
}


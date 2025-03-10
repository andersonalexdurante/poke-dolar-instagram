package com.andersonalexdurante.services;

import com.andersonalexdurante.dto.PokemonDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.*;

import java.util.List;
import java.util.Map;

@ApplicationScoped
public class BedrockService {

    private static final Logger LOGGER = LoggerFactory.getLogger(BedrockService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    @ConfigProperty(name = "BEDROCK_CAPTION_PROMPT_ARN")
    String bedrockCaptionPromptArn;

    @ConfigProperty(name = "BEDROCK_IMAGE_BACKGROUND_PROMPT_ARN")
    String bedrockImageBackgroundPromptArn;

    @Inject
    BedrockRuntimeClient bedrockClient;


    public String generateImageBackgroundDescription(String requestId, PokemonDTO pokemonDTO) {
        Map<String, PromptVariableValues> variables = Map.of(
                "pokemon_data", PromptVariableValues.builder().text(toJson(pokemonDTO)).build()
        );

        String result = sendRequestToBedrock(requestId, this.bedrockImageBackgroundPromptArn, variables);
        return result != null ? result : "";
    }

    public String generateCaption(String requestId, PokemonDTO pokemonData, Boolean dollarUp,
                                  String dollarExchangeRate, List<Map<String, Object>> last4Posts) {
        String dollarVariation = Boolean.TRUE.equals(dollarUp) ? "subiu" : Boolean.FALSE.equals(dollarUp) ? "caiu" : null;

        List<String> captions = last4Posts.stream().map(post -> post.get("caption").toString()).toList();

        Map<String, PromptVariableValues> variables = Map.of(
                "dollar_variation", PromptVariableValues.builder().text(dollarVariation).build(),
                "dollar_price", PromptVariableValues.builder().text(dollarExchangeRate).build(),
                "pokemon_data", PromptVariableValues.builder().text(toJson(pokemonData)).build(),
                "captions_history", PromptVariableValues.builder().text(toJson(captions)).build()
        );

        String result = sendRequestToBedrock(requestId, bedrockCaptionPromptArn, variables);
        return result != null ? result : "#" + pokemonData.number() + " - " + pokemonData.name();
    }

    private String toJson(Object object) {
        try {
            return objectMapper.writeValueAsString(object);
        } catch (Exception ex) {
            LOGGER.error("[ERROR] Failed to serialize object to JSON: {}", ex.getMessage(), ex);
            return "";
        }
    }

    private String sendRequestToBedrock(String requestId, String modelId, Map<String, PromptVariableValues> variables) {
        try {
            ConverseRequest request = ConverseRequest.builder()
                    .modelId(modelId)
                    .promptVariables(variables)
                    .build();

            LOGGER.info("[{}] Sending request to AWS Bedrock...", requestId);
            ConverseResponse response = bedrockClient.converse(request);

            String outputText = response.output().message().content().getFirst().text();
            LOGGER.info("[{}] AWS Bedrock output received successfully!", requestId);
            return outputText;
        } catch (Exception ex) {
            LOGGER.error("[{}] [ERROR] An error occurred while calling AWS Bedrock: {}", requestId, ex.getMessage(), ex);
            return null;
        }
    }

}

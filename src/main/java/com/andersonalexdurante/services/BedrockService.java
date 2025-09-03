package com.andersonalexdurante.services;

import com.andersonalexdurante.dto.DollarVariationDTO;
import com.andersonalexdurante.dto.PokemonDTO;
import com.andersonalexdurante.dto.RandomSelection;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
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


    public String generateImageBackgroundDescription(String requestId, PokemonDTO pokemonDTO,
                                                     RandomSelection randomSelection) {

        LOGGER.info("[{}] Using prompt: {}", requestId, bedrockImageBackgroundPromptArn);
        Map<String, PromptVariableValues> variables = Map.of(
                "types", PromptVariableValues.builder().text(toJson(pokemonDTO.types())).build(),
                "habitat", PromptVariableValues.builder().text(pokemonDTO.habitat()).build(),
                "time_of_day", PromptVariableValues.builder().text(randomSelection.timeOfDay().getValue()).build(),
                "season", PromptVariableValues.builder().text(randomSelection.season().getValue()).build(),
                "weather", PromptVariableValues.builder().text(randomSelection.weather().getValue()).build(),
                "pokemon_descriptions",  PromptVariableValues.builder().text(toJson(pokemonDTO.descriptions())).build());

        String result = sendRequestToBedrock(requestId, this.bedrockImageBackgroundPromptArn, variables);
        LOGGER.info("{}", result);
        return result != null ? result : "";
    }

    public String generateCaption(String requestId, PokemonDTO pokemonData, DollarVariationDTO dollarVariationDTO,
                                  String dollarExchangeRate) {
        String dollarVariation = String.format("%s %s",
                dollarVariationDTO.isUp() ? "subiu" : "caiu",
                dollarVariationDTO.variation().toString().replace(".", ","));

        LocalDate actualDate = LocalDate.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yy");
        String formattedDate = actualDate.format(formatter);

        Map<String, PromptVariableValues> variables = Map.of(
                "dollar_variation", PromptVariableValues.builder().text(dollarVariation).build(),
                "dollar_price", PromptVariableValues.builder().text(dollarExchangeRate.substring(0,4)).build(),
                "day_of_week", PromptVariableValues.builder().text(formattedDate).build(),
                "pokemon_name", PromptVariableValues.builder().text(pokemonData.name()).build(),
                "pokemon_types", PromptVariableValues.builder().text(toJson(pokemonData.types())).build(),
                "pokemon_descriptions",
                PromptVariableValues.builder().text(toJson(pokemonData.descriptions())).build());

        String result = sendRequestToBedrock(requestId, this.bedrockCaptionPromptArn, variables);
        if (result != null && result.startsWith("\"") && result.endsWith("\"")) {
            result = result.replaceAll("^\"|\"$", "");
        }
        LOGGER.info("Caption: {}", result);
        return result != null ? result : "#" + pokemonData.number() + " - " + pokemonData.name();
    }

    private String toJson(Object object) {
        try {
            return this.objectMapper.writeValueAsString(object);
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
            ConverseResponse response = this.bedrockClient.converse(request);

            String outputText = response.output().message().content().getFirst().text();
            LOGGER.info("[{}] AWS Bedrock output received successfully!", requestId);
            return outputText;
        } catch (Exception ex) {
            LOGGER.error("[{}] [ERROR] An error occurred while calling AWS Bedrock: {}", requestId, ex.getMessage(), ex);
            return null;
        }
    }

}

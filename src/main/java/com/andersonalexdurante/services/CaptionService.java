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
public class CaptionService {

    private static final Logger LOGGER = LoggerFactory.getLogger(CaptionService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    @ConfigProperty(name = "BEDROCK_CAPTION_PROMPT_ARN")
    String bedrockCaptionPromptArn;

    @Inject
    BedrockRuntimeClient bedrockClient;

    public CaptionService() {
        this.bedrockClient = BedrockRuntimeClient.create();
    }

    public String generateCaption(String requestId, PokemonDTO pokemonData, Boolean dollarup,
                                  String dollarExchangeRate, List<String> last4Posts) {

        String dollarVariation = null;

        if (Boolean.TRUE == dollarup) dollarVariation = "subiu";
        else if (Boolean.FALSE == dollarup) dollarVariation = "caiu";

        try {
            String pokemonJsonPayload = this.objectMapper.writeValueAsString(pokemonData);
            String captionsHistoryPayload = this.objectMapper.writeValueAsString(last4Posts);
            Map<String, PromptVariableValues> variables = Map.of(
                    "dollar_variation", PromptVariableValues.builder().text(dollarVariation).build(),
                    "dollar_price", PromptVariableValues.builder().text(dollarExchangeRate).build(),
                    "pokemon_data", PromptVariableValues.builder().text(pokemonJsonPayload).build(),
                    "captions_history", PromptVariableValues.builder().text(captionsHistoryPayload).build()
                    );

            ConverseRequest request = ConverseRequest.builder()
                    .modelId(this.bedrockCaptionPromptArn)
                    .promptVariables(variables)
                    .build();

            LOGGER.info("[{}] Sending request to generate caption in AWS Bedrock...", requestId);

            ConverseResponse response = this.bedrockClient.converse(request);

            String outputText = response.output().message().content().getFirst().text();
            LOGGER.info("[{}] AWS Bedrock output received succesfully!", requestId);

            return outputText;
        } catch (Exception ex) {
            LOGGER.error("[{}] [ERROR] An error ocurred trying to call AWS Bedrock. - {}", requestId, ex.getMessage(),
                    ex);
            LOGGER.info("[{}] Returning default caption", requestId);
            return "#" + pokemonData.number() + " - " + pokemonData.name();
        }
    }
}

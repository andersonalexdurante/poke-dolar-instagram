package com.andersonalexdurante;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.andersonalexdurante.dto.PokemonDTO;
import com.andersonalexdurante.services.*;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import java.io.InputStream;
import java.net.URL;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class PokeDolarLambdaHandler implements RequestHandler<Object, Void> {

    private static final Logger LOGGER = LoggerFactory.getLogger(PokeDolarLambdaHandler.class);

    @Inject
    DollarService dollarService;
    @Inject
    PokemonService pokemonService;
    @Inject
    S3Service s3Service;
    @Inject
    ImageEditingService imageEditingService;
    @Inject
    CaptionService captionService;
    @Inject
    InstagramService instagramService;

    @Override
    public Void handleRequest(Object event, Context context) {
        String requestId = UUID.randomUUID().toString();
        MDC.put("requestId", requestId);

        LOGGER.info("[{}] [START] Executing Pokemon Image Generator Lambda", requestId);

        try {
            String dollarExchangeRate = this.dollarService.getDollarExchangeRate(requestId);
            int pokedexNumber = this.getPokedexNumber(dollarExchangeRate);
            Optional<PokemonDTO> lastPublishedPokemonOpt = this.s3Service.getLastPublishedPokemon(requestId);

            if (this.isPokemonAlreadyPublished(lastPublishedPokemonOpt, pokedexNumber)) {
                LOGGER.info("[{}] The Pokemon #{} has already been published. Skipping", requestId, pokedexNumber);
                return null;
            }

            LOGGER.info("[{}] Fetching Pokemon data for Pokedex #{}", requestId, pokedexNumber);
            PokemonDTO pokemonData = this.pokemonService.getPokemonData(requestId, pokedexNumber);

            LOGGER.info("[{}] Analyzing whether the price of the dollar rose or fell", requestId);
            Boolean dollarUp = dollarUp(requestId, lastPublishedPokemonOpt.orElse(null), pokedexNumber);

            LOGGER.info("[{}] Generating post caption with AWS Bedrock", requestId);
            String postCaption = this.captionService.generateCaption(requestId, pokemonData, dollarUp,
            dollarExchangeRate);

            LOGGER.info("[{}] Getting Pokemon image from S3. Pokedex: #{}", requestId, pokedexNumber);
            InputStream pokemonImageStream = this.s3Service.getPokemonImage(requestId, pokedexNumber);

            LOGGER.info("[{}] Editing Pokemon image with exchange rate ${}", requestId, dollarExchangeRate);
            InputStream editedPokemonImage = this.imageEditingService.editPokemonImage(requestId, dollarExchangeRate,
                    dollarUp, pokemonData, lastPublishedPokemonOpt.orElse(null), pokemonImageStream);

            LOGGER.info("[{}] Saving edited image to S3", requestId);
            URL imageUrlToPublish = this.s3Service.savePokemonImage(requestId, editedPokemonImage);

            LOGGER.info("[{}] Posting image to Instagram. Pokedex: #{}", requestId, pokedexNumber);
            this.instagramService.postPokemonImage(requestId, pokedexNumber, pokemonData, imageUrlToPublish,
                    postCaption);

            LOGGER.info("[{}] Saving last published Pokemon info", requestId);
            this.s3Service.saveLastPublishedPokemonJSON(requestId, pokemonData);
        } catch (Exception e) {
            LOGGER.error("[{}] [ERROR] An unexpected error occurred. - {}", requestId, e.getMessage(), e);
        } finally {
            LOGGER.info("[{}] [END] Execution finished", requestId);
            MDC.clear();
        }

        return null;
    }

    private static Boolean dollarUp(String requestId, PokemonDTO lastPokemonData, int pokedexNumber) {
        if (Objects.isNull(lastPokemonData)) {
            LOGGER.info("[{}] No previous data to say whether the dollar rose or fell", requestId);
            return null;
        } else if(pokedexNumber > lastPokemonData.number()) {
            LOGGER.info("[{}] BRL to USD rose: {} - {}", requestId, lastPokemonData.number(), pokedexNumber);
            return Boolean.TRUE;
        }
        LOGGER.info("[{}] BRL to USD fell: {} - {}", requestId, lastPokemonData.number(), pokedexNumber);
        return Boolean.FALSE;
    }

    private int getPokedexNumber(String dollarExchangeRate) {
        int pokedexNumber = Integer.parseInt(dollarExchangeRate.replace(",", ""));
        LOGGER.debug("Calculated Pokedex number: #{}. Dollar Rate: ${}", pokedexNumber, dollarExchangeRate);
        return pokedexNumber;
    }

    private boolean isPokemonAlreadyPublished(Optional<PokemonDTO> lastPublishedPokemonOpt, int pokedexNumber) {
        boolean isPublished = lastPublishedPokemonOpt.isPresent() &&
                isLastPokemonPublished(lastPublishedPokemonOpt.get().number(), pokedexNumber);
        LOGGER.debug("Checking if Pokedex #{} is already published: {}.", pokedexNumber, isPublished);
        return isPublished;
    }

    private boolean isLastPokemonPublished(int lastPokedexNumber, int pokedexNumber) {
        boolean isSame = lastPokedexNumber == pokedexNumber;
        LOGGER.debug("Comparing last published Pokemon #{} with current #{}: {}", lastPokedexNumber, pokedexNumber, isSame);
        return isSame;
    }
}
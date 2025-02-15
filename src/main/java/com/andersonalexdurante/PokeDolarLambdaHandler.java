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
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class PokeDolarLambdaHandler implements RequestHandler<Object, Void> {

    private static final Logger LOGGER = LoggerFactory.getLogger(PokeDolarLambdaHandler.class);

    @Inject
    DollarService dollarService;
    @Inject
    PokemonApiService pokemonApiService;
    @Inject
    S3Service s3Service;
    @Inject
    InstagramService instagramService;

    @Override
    public Void handleRequest(Object event, Context context) {
        String requestId = UUID.randomUUID().toString();
        MDC.put("requestId", requestId);

        LOGGER.info("[{}] [START] Executing Pokemon Image Generator Lambda", requestId);

        try {
            Double dollarExchangeRate = this.dollarService.getDollarExchangeRate(requestId);
            int pokedexNumber = this.getPokedexNumber(dollarExchangeRate);
            Optional<PokemonDTO> lastPublishedPokemonOpt = this.s3Service.getLastPublishedPokemon(requestId);

            if (this.isPokemonAlreadyPublished(lastPublishedPokemonOpt, pokedexNumber)) {
                LOGGER.info("[{}] The Pokemon #{} has already been published. Skipping", requestId, pokedexNumber);
                return null;
            }

            LOGGER.info("[{}] Fetching Pokemon data for Pokedex #{}", requestId, pokedexNumber);
            PokemonDTO pokemonDTO = this.fetchPokemonData(pokedexNumber);

            LOGGER.info("[{}] Getting Pokemon image from S3. Pokedex: #{}", requestId, pokedexNumber);
            InputStream pokemonImageStream = this.s3Service.getPokemonImage(requestId, pokedexNumber);

            LOGGER.info("[{}] Editing Pokemon image with exchange rate ${}", requestId, dollarExchangeRate);
            InputStream editedPokemonImage = this.editPokemonImage(requestId, pokemonImageStream, dollarExchangeRate,
                    pokemonDTO,
                    lastPublishedPokemonOpt.orElse(null));

            LOGGER.info("[{}] Saving edited image to S3", requestId);
            URL imageUrlToPublish = this.s3Service.savePokemonImage(requestId, editedPokemonImage);

            LOGGER.info("[{}] Posting image to Instagram. Pokedex: #{}", requestId, pokedexNumber);
            this.instagramService.postPokemonImage(requestId, pokedexNumber, pokemonDTO, imageUrlToPublish);

            LOGGER.info("[{}] Saving last published Pokemon info", requestId);
            this.s3Service.saveLastPublishedPokemonJSON(requestId, pokemonDTO);

        } catch (Exception e) {
            LOGGER.error("[{}] [ERROR] An unexpected error occurred. - {}", requestId, e.getMessage(), e);
        } finally {
            LOGGER.info("[{}] [END] Execution finished", requestId);
            MDC.clear();
        }
        return null;
    }

    private int getPokedexNumber(Double dollarExchangeRate) {
        int pokedexNumber = (int) Math.floor(dollarExchangeRate * 100);
        LOGGER.debug("Calculated Pokedex number: #{}. Dollar Rate: ${}", pokedexNumber, dollarExchangeRate);
        return pokedexNumber;
    }

    private boolean isPokemonAlreadyPublished(Optional<PokemonDTO> lastPublishedPokemonOpt, int pokedexNumber) {
        boolean isPublished = lastPublishedPokemonOpt.isPresent() &&
                isLastPokemonPublished(lastPublishedPokemonOpt.get().pokedexNumber(), pokedexNumber);
        LOGGER.debug("Checking if Pokedex #{} is already published: {}.", pokedexNumber, isPublished);
        return isPublished;
    }

    private PokemonDTO fetchPokemonData(int pokedexNumber) {
        LOGGER.debug("Fetching Pokemon name for Pokedex #{}", pokedexNumber);
        String pokemonName = this.pokemonApiService.getPokemonName(pokedexNumber);
        return new PokemonDTO(pokedexNumber, pokemonName);
    }

    private InputStream editPokemonImage(String requestId, InputStream pokemonImageStream, Double dollarExchangeRate,
                                           PokemonDTO pokemonDTO, PokemonDTO lastPublishedPokemon) {
        LOGGER.debug("Editing image for Pokedex #{} with last published data.", pokemonDTO.pokedexNumber());
        ImageEditingService imageEditingService = new ImageEditingService();

        return imageEditingService.editPokemonImage(requestId, dollarExchangeRate, pokemonDTO, lastPublishedPokemon,
                pokemonImageStream);
    }

    private boolean isLastPokemonPublished(int lastPokedexNumber, int pokedexNumber) {
        boolean isSame = lastPokedexNumber == pokedexNumber;
        LOGGER.debug("Comparing last published Pokemon #{} with current #{}: {}", lastPokedexNumber, pokedexNumber, isSame);
        return isSame;
    }
}
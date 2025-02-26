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
import java.math.BigDecimal;
import java.net.URL;
import java.util.List;
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
    DynamoDBService dynamoDBService;
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
            Optional<String> lastDollarRate = this.dynamoDBService.getLastDollarRate(requestId);

            if (!this.dollarRateChanged(lastDollarRate, dollarExchangeRate)) {
                LOGGER.info("[{}] Dollar rate {} dont changed! The Pokemon #{} has already been published. Skipping",
                        requestId, dollarExchangeRate, pokedexNumber);
                return null;
            }

            LOGGER.info("[{}] Fetching Pokemon data for Pokedex #{}", requestId, pokedexNumber);
            PokemonDTO pokemonData = this.pokemonService.getPokemonData(requestId, pokedexNumber);

            LOGGER.info("[{}] Analyzing whether the price of the dollar rose or fell", requestId);
            Boolean dollarUp = dollarUp(requestId, lastDollarRate.orElse(null), dollarExchangeRate);

            LOGGER.info("[{}] Getting Last Posts for caption prompt context", requestId);
            List<String> last4Captions = this.dynamoDBService.getLast4Captions(requestId);

            LOGGER.info("[{}] Generating post caption with AWS Bedrock", requestId);
            String postCaption = this.captionService.generateCaption(requestId, pokemonData, dollarUp,
            dollarExchangeRate, last4Captions);

            LOGGER.info("[{}] Getting Pokemon image from S3. Pokedex: #{}", requestId, pokedexNumber);
            InputStream pokemonImageStream = this.s3Service.getPokemonImage(requestId, pokemonData.name());

            LOGGER.info("[{}] Editing Pokemon image with exchange rate ${}", requestId, dollarExchangeRate);
            InputStream editedPokemonImage = this.imageEditingService.editPokemonImage(requestId, dollarExchangeRate,
                    dollarUp, pokemonData, pokemonImageStream);

            LOGGER.info("[{}] Saving edited image to S3", requestId);
            URL imageUrlToPublish = this.s3Service.savePokemonImage(requestId, editedPokemonImage);

            LOGGER.info("[{}] Posting image to Instagram. Pokedex: #{}", requestId, pokedexNumber);
            this.instagramService.postPokemonImage(requestId, pokedexNumber, pokemonData, imageUrlToPublish,
                    postCaption);

            LOGGER.info("[{}] Saving new post info in DynamoDB", requestId);
            this.dynamoDBService.savePost(requestId, pokemonData.name(), dollarExchangeRate, postCaption);
        } catch (Exception e) {
            LOGGER.error("[{}] [ERROR] An unexpected error occurred. - {}", requestId, e.getMessage(), e);
        } finally {
            LOGGER.info("[{}] [END] Execution finished", requestId);
            MDC.clear();
        }

        return null;
    }

    private int getPokedexNumber(String dollarExchangeRate) {
        int pokedexNumber = Integer.parseInt(dollarExchangeRate.replace(",", ""));
        LOGGER.debug("Calculated Pokedex number: #{}. Dollar Rate: ${}", pokedexNumber, dollarExchangeRate);
        return pokedexNumber;
    }

    private boolean dollarRateChanged(Optional<String> lastDollarRate, String dollarExchangeRate) {
        boolean dollarRateChanged = lastDollarRate.isEmpty() ||
                lastDollarRate.map(s -> !s.equals(dollarExchangeRate)).orElse(true);
        LOGGER.debug("Checking if Dollar Rate changed: {}", dollarRateChanged);
        return dollarRateChanged;
    }


    private static Boolean dollarUp(String requestId, String lastDollarRate, String dollarExchangeRate) {
        if (lastDollarRate == null) {
            LOGGER.info("[{}] No previous data to determine whether the dollar rose or fell", requestId);
            return null;
        }

        BigDecimal lastRate = new BigDecimal(lastDollarRate.replace(",", "."));
        BigDecimal newRate = new BigDecimal(dollarExchangeRate.replace(",", "."));

        if (newRate.compareTo(lastRate) > 0) {
            LOGGER.info("[{}] BRL to USD rose: {} → {}", requestId, lastRate, newRate);
            return Boolean.TRUE;
        }

        LOGGER.info("[{}] BRL to USD fell: {} → {}", requestId, lastRate, newRate);
        return Boolean.FALSE;
    }

}
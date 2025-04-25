package com.andersonalexdurante;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.andersonalexdurante.dto.DollarVariationDTO;
import com.andersonalexdurante.dto.PokemonDTO;
import com.andersonalexdurante.interfaces.IDollarService;
import com.andersonalexdurante.services.*;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.net.URL;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class PokeDolarLambdaHandler implements RequestHandler<Object, Void> {

    private static final Logger LOGGER = LoggerFactory.getLogger(PokeDolarLambdaHandler.class);

    @Inject
    @Named("dollarService")
    IDollarService dollarService;
    @Inject
    PokemonService pokemonService;
    @Inject
    DynamoDBService dynamoDBService;
    @Inject
    S3Service s3Service;
    @Inject
    VideoService videoService;
    @Inject
    BedrockService bedrockService;
    @Inject
    InstagramService instagramService;

    @Override
    public Void handleRequest(Object event, Context context) {
        String requestId = UUID.randomUUID().toString();
        MDC.put("requestId", requestId);

        LOGGER.info("[{}] [START] Executing Pokemon Image Generator Lambda", requestId);

        try {
            String dollarExchangeRate = this.dollarService.getDollarExchangeRate(requestId);
            int pokedexNumber = this.pokemonService.getPokedexNumber(dollarExchangeRate);
            Optional<String> lastDollarRate = this.dynamoDBService.getLastDollarRate(requestId);

            if (!this.dollarService.dollarRateChanged(lastDollarRate, dollarExchangeRate)) {
                LOGGER.info("[{}] Dollar rate {} dont changed! The Pokemon #{} has already been published. Skipping",
                        requestId, dollarExchangeRate, pokedexNumber);
                return null;
            }

            LOGGER.info("[{}] Fetching Pokemon data for Pokedex #{}", requestId, pokedexNumber);
            PokemonDTO pokemonData = this.pokemonService.getPokemonData(requestId, pokedexNumber);

            LOGGER.info("[{}] Analyzing whether the price of the dollar rose or fell", requestId);
            DollarVariationDTO dollarVariation = this.dollarService.getDollarVariation(requestId,
                    lastDollarRate.orElse("0"), dollarExchangeRate);

            LOGGER.info("[{}] Generating image background description with AWS Bedrock", requestId);
            String backgroundImageDescription = this.bedrockService.generateImageBackgroundDescription(requestId,
                    pokemonData);

            LOGGER.info("[{}] Starting video generation", requestId);
            this.videoService.generatePostVideo(requestId, dollarExchangeRate,
                    dollarVariation.isUp(), pokemonData, backgroundImageDescription);

            LOGGER.info("[{}] Getting post video URL from S3", requestId);
            URL postVideoUrl = this.s3Service.getPostVideoUrl(requestId);

            LOGGER.info("[{}] Generating post caption with AWS Bedrock", requestId);
            String postCaption = this.bedrockService.generateCaption(requestId, pokemonData, dollarVariation,
                    dollarExchangeRate);

            LOGGER.info("[{}] Posting video to Instagram", requestId);
            this.instagramService.post(requestId, pokedexNumber, postVideoUrl,
                    postCaption);

            LOGGER.info("[{}] Saving new post in DynamoDB", requestId);
            this.dynamoDBService.savePost(requestId, pokemonData.name(), dollarExchangeRate, postCaption);
        } catch (Exception e) {
            LOGGER.error("[{}] [ERROR] An unexpected error occurred. - {}", requestId, e.getMessage(), e);
        } finally {
            LOGGER.info("[{}] [END] Execution finished", requestId);
            MDC.clear();
        }

        return null;
    }
}
package com.andersonalexdurante.services;

import com.andersonalexdurante.dto.PokemonDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.MediaType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import java.io.*;
import java.net.URL;
import java.time.Duration;
import java.util.Optional;
import java.util.stream.Collectors;

@ApplicationScoped
public class S3Service {

    private static final Logger LOGGER = LoggerFactory.getLogger(S3Service.class);
    private static final String BUCKET = "pokedolarbucket";
    private static final String POKEMON_IMAGES_FOLDER = "pokemon/";
    private static final String LAST_POKEMON_JSON_KEY = "lastPublishedPokemon.json";
    private static final String LAST_POKEMON_IMAGE_KEY = "lastPublishedPokemonImage.png";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Inject
    S3Client s3Client;
    @Inject
    S3Presigner s3Presigner;

    public InputStream getPokemonImage(String requestId, int pokedexNumber) {
        String key = POKEMON_IMAGES_FOLDER + pokedexNumber + ".png";
        LOGGER.info("[{}] Fetching Pokemon image from S3. Pokedex: #{}", requestId, pokedexNumber);

        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(BUCKET)
                .key(key)
                .build();

        try {
            return this.s3Client.getObject(getObjectRequest);
        } catch (S3Exception ex) {
            LOGGER.error("[{}] Failed to fetch image from S3. Pokedex: #{}. - {}", requestId, pokedexNumber,
                    ex.getMessage(), ex);
            throw new RuntimeException("Failed to fetch image from S3", ex);
        }
    }

    public URL savePokemonImage(String requestId, InputStream inputStream) {
        LOGGER.info("[{}] Saving Pokemon image to S3", requestId);

        try {
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(BUCKET)
                    .key(LAST_POKEMON_IMAGE_KEY)
                    .contentType("image/png")
                    .build();

            long contentLength = inputStream.available();
            this.s3Client.putObject(putObjectRequest, RequestBody.fromInputStream(inputStream, contentLength));

            LOGGER.info("[{}] Image uploaded to S3 successfully!", requestId);
            return this.generatePresignedUrl(requestId, LAST_POKEMON_IMAGE_KEY);
        } catch (Exception ex) {
            LOGGER.error("[{}] Failed to save image to S3. - {}", requestId, ex.getMessage(), ex);
            throw new RuntimeException("Failed to save image to S3", ex);
        }
    }

    private URL generatePresignedUrl(String requestId, String key) {
        LOGGER.info("[{}] Generating presigned URL for key: {}", requestId, key);

        try {
            GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                    .bucket(BUCKET)
                    .key(key)
                    .build();

            PresignedGetObjectRequest presignedGetObjectRequest = this.s3Presigner.presignGetObject(
                    GetObjectPresignRequest.builder()
                            .signatureDuration(Duration.ofMinutes(2))
                            .getObjectRequest(getObjectRequest)
                            .build()
            );
            LOGGER.info("[{}] Presigned URL generated successfully!", requestId);
            return presignedGetObjectRequest.url();
        } catch (Exception ex) {
            LOGGER.error("[{}] Failed to generate presigned URL. - {}", requestId, ex.getMessage(), ex);
            throw new RuntimeException("Failed to generate presigned URL", ex);
        }
    }

    public Optional<PokemonDTO> getLastPublishedPokemon(String requestId) {
        LOGGER.info("[{}] Fetching last published Pokemon JSON from S3", requestId);

        try {
            GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                    .bucket(BUCKET)
                    .key(LAST_POKEMON_JSON_KEY)
                    .build();

            ResponseInputStream<GetObjectResponse> response = this.s3Client.getObject(getObjectRequest);
            String jsonContent = new BufferedReader(new InputStreamReader(response))
                    .lines()
                    .collect(Collectors.joining("\n"));

            PokemonDTO lastPostedPokemon = this.objectMapper.readValue(jsonContent, PokemonDTO.class);
            return Optional.of(lastPostedPokemon);
        } catch (NoSuchKeyException e) {
            LOGGER.warn("[{}] No previous published Pokemon found in S3", requestId);
            return Optional.empty();
        } catch (Exception e) {
            LOGGER.error("[{}] Failed to fetch last published Pokemon JSON from S3. - {}", requestId,
                    e.getMessage(), e);
            throw new RuntimeException("Failed to fetch last published Pokemon", e);
        }
    }

    public void saveLastPublishedPokemonJSON(String requestId, PokemonDTO pokemonDTO) {
        LOGGER.info("[{}] Saving last published Pokemon JSON to S3. Pokedex: #{}", requestId, pokemonDTO.number());

        try {
            String jsonContent = this.objectMapper.writeValueAsString(pokemonDTO);
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(BUCKET)
                    .key(LAST_POKEMON_JSON_KEY)
                    .contentType(MediaType.APPLICATION_JSON)
                    .build();

            this.s3Client.putObject(putObjectRequest, RequestBody.fromString(jsonContent));
            LOGGER.info("[{}] Successfully saved JSON of the last published Pokemon", requestId);
        } catch (Exception e) {
            LOGGER.error("[{}] Failed to save last published Pokemon JSON to S3. - {}", requestId,
                    e.getMessage(), e);
            throw new RuntimeException("Failed to save last published Pokemon", e);
        }
    }
}

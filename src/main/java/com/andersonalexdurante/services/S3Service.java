package com.andersonalexdurante.services;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import java.util.List;

import java.io.*;
import java.net.URL;
import java.time.Duration;
import java.util.Random;

@ApplicationScoped
public class S3Service {

    private static final Logger LOGGER = LoggerFactory.getLogger(S3Service.class);
    private static final String BUCKET = "pokedolarbucket";
    private static final String LAST_POST_VIDEO = "lastPost.mp4";
    private static final String SPRITES_FOLDER = "sprites/";

    @Inject
    S3Client s3Client;
    @Inject
    S3Presigner s3Presigner;

    public InputStream getPokemonImage(String requestId, String pokemonName) {
        String searchPrefix = SPRITES_FOLDER + pokemonName.toLowerCase();
        LOGGER.info("[{}] Searching for Pokemon images in S3. Base name: {}", requestId, pokemonName);

        try {
            ListObjectsV2Request listFoldersRequest = ListObjectsV2Request.builder()
                    .bucket(BUCKET)
                    .prefix(searchPrefix)
                    .delimiter("/") // Ensures we get only folders
                    .build();
            ListObjectsV2Response foldersResponse = this.s3Client.listObjectsV2(listFoldersRequest);

            List<String> matchingFolders = foldersResponse.commonPrefixes().stream()
                    .map(CommonPrefix::prefix)
                    .toList();

            if (matchingFolders.isEmpty()) {
                throw new RuntimeException("No folder found for Pokemon: " + pokemonName);
            }

            String chosenFolder = matchingFolders.getFirst();
            LOGGER.info("[{}] Found Pokemon folder: {}", requestId, chosenFolder);

            ListObjectsV2Request listImagesRequest = ListObjectsV2Request.builder()
                    .bucket(BUCKET)
                    .prefix(chosenFolder)
                    .build();
            ListObjectsV2Response imagesResponse = this.s3Client.listObjectsV2(listImagesRequest);

            List<String> imageKeys = imagesResponse.contents().stream()
                    .map(S3Object::key)
                    .filter(key -> key.endsWith(".png"))
                    .toList();

            if (imageKeys.isEmpty()) {
                throw new RuntimeException("No images found in folder: " + chosenFolder);
            }

            String chosenImage = imageKeys.get(new Random().nextInt(imageKeys.size()));
            LOGGER.info("[{}] Selected Pokemon image: {}", requestId, chosenImage);

            GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                    .bucket(BUCKET)
                    .key(chosenImage)
                    .build();

            return this.s3Client.getObject(getObjectRequest);
        } catch (S3Exception ex) {
            LOGGER.error("[{}] Failed to fetch Pokemon image from S3: {} - {}", requestId, pokemonName, ex.getMessage(),
                    ex);
            throw new RuntimeException("Failed to fetch Pokemon image from S3", ex);
        }
    }

    public URL getPostVideoUrl(String requestId) {
        return this.generatePresignedUrl(requestId, LAST_POST_VIDEO);
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
}

package com.andersonalexdurante.services;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import java.net.URL;
import java.time.Duration;

@ApplicationScoped
public class S3Service {

    private static final Logger LOGGER = LoggerFactory.getLogger(S3Service.class);
    private static final String BUCKET = "pokedolarbucket";
    private static final String LAST_POST_VIDEO = "lastPost.mp4";

    @Inject
    S3Presigner s3Presigner;

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

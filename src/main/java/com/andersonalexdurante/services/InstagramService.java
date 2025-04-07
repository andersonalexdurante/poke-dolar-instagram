package com.andersonalexdurante.services;

import com.andersonalexdurante.dto.CreateMediaContainerDTO;
import com.andersonalexdurante.dto.PublishMediaContainerDTO;
import com.andersonalexdurante.exceptions.InstagramApiException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.stream.IntStream;

@ApplicationScoped
public class InstagramService {

    private static final Logger LOGGER = LoggerFactory.getLogger(InstagramService.class);
    private static final String INSTAGRAM_ACCESS_TOKEN_PARAMETER = "instagram_access_token";
    private final ObjectMapper objectMapper = new ObjectMapper();
    @ConfigProperty(name = "INSTAGRAM_GRAPH_API_URL")
    String instagramGraphApiUrl;
    @ConfigProperty(name = "INSTAGRAM_POKEDOLAR_USERID")
    String instagramPokedolarUserId;

    @Inject
    SsmService ssmService;

    public void post(String requestId, int pokedexNumber, URL postVideoUrl, String postCaption) {
        LOGGER.info("[{}] Starting Instagram post... Pokemon: #{}", requestId, pokedexNumber);
        try {
            String accessToken = this.ssmService.getStringParameterWithDecryption(requestId, INSTAGRAM_ACCESS_TOKEN_PARAMETER);
            String idMediaContainer = this.createMediaContainer(requestId, postVideoUrl, postCaption, pokedexNumber, accessToken);

            boolean ready = this.waitUntilMediaIsReady(requestId, idMediaContainer, accessToken);
            if (!ready) {
                throw new InstagramApiException("Media is not ready after waiting. Aborting publish.");
            }

            String idPublishedContainer = this.publishMediaContainer(requestId, idMediaContainer, accessToken);
            LOGGER.info("[{}] Pokemon video posted successfully! ID: {}", requestId, idPublishedContainer);
        } catch (Exception e) {
            LOGGER.error("[{}] Error while posting video to Instagram!", requestId, e);
            throw new InstagramApiException("Error while posting video to Instagram.", e);
        }
    }


    private String createMediaContainer(String requestId, URL postVideoUrl, String postCaption,
                                        int pokedexNumber, String accessToken) {
        try {
            CreateMediaContainerDTO createMediaContainerDTO =
                    new CreateMediaContainerDTO(postVideoUrl.toString(), postCaption);

            LOGGER.info("[{}] Creating Media Container for Pokemon #{}...", requestId, pokedexNumber);

            URI createMediaContainerUri = URI.create(this.instagramGraphApiUrl + this.instagramPokedolarUserId
                    + "/media" + "?access_token=" + accessToken);

            HttpResponse<String> response = HttpClient.newHttpClient().send(HttpRequest.newBuilder()
                    .header("Content-Type", MediaType.APPLICATION_JSON)
                    .uri(createMediaContainerUri)
                    .POST(HttpRequest.BodyPublishers.ofString(this.objectMapper.writeValueAsString(createMediaContainerDTO)))
                    .build(), HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JsonNode rootNode = this.objectMapper.readTree(response.body());
                String id = rootNode.path("id").asText();
                LOGGER.info("[{}] Media container created successfully! ID: {}", requestId, id);
                return id;
            }

            LOGGER.error("[{}] Failed to create media container. HTTP status: {}", requestId, response.statusCode());
            throw new InstagramApiException("Failed to create media container. HTTP status: " + response.statusCode());
        } catch (Exception ex) {
            LOGGER.error("[{}] Error while creating media container!", requestId, ex);
            throw new InstagramApiException("Error while creating media container.", ex);
        }
    }

    private String publishMediaContainer(String requestId, String idMediaContainer, String accessToken) {
        try {
            PublishMediaContainerDTO publishMediaContainerDTO = new PublishMediaContainerDTO(idMediaContainer);

            LOGGER.info("[{}] Publishing Media Container ID: {}...", requestId, idMediaContainer);

            URI publishMediaUri = URI.create(this.instagramGraphApiUrl + this.instagramPokedolarUserId
                    + "/media_publish" + "?access_token=" + accessToken);

            HttpResponse<String> response = HttpClient.newHttpClient().send(HttpRequest.newBuilder()
                    .header("Content-Type", MediaType.APPLICATION_JSON)
                    .uri(publishMediaUri)
                    .POST(HttpRequest.BodyPublishers.ofString(this.objectMapper.writeValueAsString(publishMediaContainerDTO)))
                    .build(), HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JsonNode rootNode = this.objectMapper.readTree(response.body());
                String id = rootNode.path("id").asText();
                LOGGER.info("[{}] Media container published successfully! ID: {}", requestId, id);
                return id;
            }

            LOGGER.error("[{}] Failed to publish media container. HTTP status: {}", requestId, response.statusCode());
            throw new InstagramApiException("Failed to publish media container. HTTP status: " + response.statusCode());
        } catch (Exception ex) {
            LOGGER.error("[{}] Error while publishing media container!", requestId, ex);
            throw new InstagramApiException("Error while publishing media container.", ex);
        }
    }

    private boolean waitUntilMediaIsReady(String requestId, String mediaId, String accessToken) {
        final int maxAttempts = 10;
        final Duration delay = Duration.ofSeconds(5);
        final HttpClient client = HttpClient.newHttpClient();

        URI verifyMediaStatus = URI.create(this.instagramGraphApiUrl + mediaId + "?fields=status_code" +
                "&access_token=" + accessToken);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(verifyMediaStatus)
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();

        return IntStream.range(0, maxAttempts)
                .mapToObj(attempt -> {
                    try {
                        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                        String status = objectMapper.readTree(response.body()).path("status_code").asText();

                        LOGGER.info("[{}] Attempt {}/{} - Media status for ID {}: {}", requestId, attempt + 1,
                                maxAttempts, mediaId, status);

                        if ("FINISHED".equalsIgnoreCase(status)) return true;

                        Thread.sleep(delay.toMillis());
                    } catch (Exception e) {
                        LOGGER.warn("[{}] Attempt {}/{} - Error checking media status: {}", requestId, attempt + 1,
                                maxAttempts, e.getMessage());
                        try {
                            Thread.sleep(delay.toMillis());
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            return false;
                        }
                    }
                    return false;
                })
                .anyMatch(ready -> ready);
    }

}
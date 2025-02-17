package com.andersonalexdurante.services;

import com.andersonalexdurante.dto.CreateMediaContainerDTO;
import com.andersonalexdurante.dto.PokemonDTO;
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
import software.amazon.awssdk.services.ssm.SsmClient;
import software.amazon.awssdk.services.ssm.model.GetParameterRequest;
import software.amazon.awssdk.services.ssm.model.GetParameterResponse;
import software.amazon.awssdk.services.ssm.model.SsmException;

import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

@ApplicationScoped
public class InstagramService {

    private static final Logger LOGGER = LoggerFactory.getLogger(InstagramService.class);
    private static final String INSTAGRAM_ACCESS_TOKEN_PARAMETER = "instagram_access_token";
    private final ObjectMapper objectMapper = new ObjectMapper();
    @ConfigProperty(name = "INSTAGRAM_CREATE_MEDIA_CONTAINER_URL")
    String createMediaContainerUrl;
    @ConfigProperty(name = "INSTAGRAM_PUBLISH_MEDIA_CONTAINER_URL")
    String publishMediaContainerUrl;

    @Inject
    SsmClient ssmClient;


    public void postPokemonImage(String requestId, int pokedexNumber,
                                 PokemonDTO pokemonDTO, URL pokemonImageUrl, String postCaption) {
        LOGGER.info("[{}] Starting Instagram post... Pokemon: #{}", requestId, pokedexNumber);
        try {
            String accessToken = this.getAccessToken(requestId);
            String idMediaContainer = this.createMediaContainer(requestId, pokemonImageUrl,
                    postCaption, pokedexNumber, accessToken);
            String idPublishedContainer = this.publishMediaContainer(requestId, idMediaContainer, accessToken);
            LOGGER.info("[{}] Pokemon image posted successfully! ID: {}", requestId, idPublishedContainer);
        } catch (Exception e) {
            LOGGER.error("[{}] Error while posting image to Instagram!", requestId, e);
        }
    }

    public String getAccessToken(String requestId) {
        LOGGER.info("[{}] Getting Instagram Access Token", requestId);
        try {
            GetParameterRequest request = GetParameterRequest.builder()
                    .name(INSTAGRAM_ACCESS_TOKEN_PARAMETER)
                    .withDecryption(true)
                    .build();

            GetParameterResponse response = this.ssmClient.getParameter(request);
            LOGGER.info("[{}] Instagram Access Token recovered!", requestId);
            return response.parameter().value();
        } catch (SsmException e) {
            LOGGER.error("[{}] Error while getting access token from AWS Parameter Store!", requestId, e);
            throw e;
        }

    }

    private String createMediaContainer(String requestId, URL pokemonImageUrl, String postCaption,
                                        int pokedexNumber, String accessToken) {
        try {
            CreateMediaContainerDTO createMediaContainerDTO =
                    new CreateMediaContainerDTO(pokemonImageUrl.toString(), postCaption);

            LOGGER.info("[{}] Creating Media Container for Pokemon #{}...", requestId, pokedexNumber);

            HttpResponse<String> response = HttpClient.newHttpClient().send(HttpRequest.newBuilder()
                    .header("Content-Type", MediaType.APPLICATION_JSON)
                    .uri(URI.create(this.createMediaContainerUrl + "?access_token=" + accessToken))
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

            HttpResponse<String> response = HttpClient.newHttpClient().send(HttpRequest.newBuilder()
                    .header("Content-Type", MediaType.APPLICATION_JSON)
                    .uri(URI.create(this.publishMediaContainerUrl + "?access_token=" + accessToken))
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
}
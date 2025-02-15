package com.andersonalexdurante.services;

import com.andersonalexdurante.exceptions.PokeApiException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

@ApplicationScoped
public class PokemonApiService {

    private static final Logger LOGGER = LoggerFactory.getLogger(PokemonApiService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    @ConfigProperty(name = "pokeapi.url")
    String pokeApiUrl;

    public String getPokemonName(int pokedexNumber) {
        String requestId = MDC.get("requestId");
        String pokemonUrl = this.pokeApiUrl + pokedexNumber;

        LOGGER.info("[{}] Fetching Pokemon from URL: {}", requestId, pokemonUrl);

        try {
            HttpResponse<String> response = HttpClient.newHttpClient()
                    .send(HttpRequest.newBuilder()
                            .uri(URI.create(pokemonUrl))
                            .GET()
                            .build(), HttpResponse.BodyHandlers.ofString());

            int statusCode = response.statusCode();
            if (statusCode == 200) {
                JsonNode rootNode = this.objectMapper.readTree(response.body());
                String pokemonName = rootNode.get("name").asText().toUpperCase();
                LOGGER.info("[{}] Successfully fetched Pokemon: {} (Pokedex Number: {})", requestId,
                        pokemonName, pokedexNumber);
                return pokemonName;
            } else {
                LOGGER.error("[{}] Failed to fetch Pokemon. HTTP Status: {} | URL: {}", requestId,
                        statusCode, pokemonUrl);
                throw new PokeApiException("Failed to fetch PokeAPI. HTTP status: " + statusCode);
            }
        } catch (Exception ex) {
            LOGGER.error("[{}] Exception occurred while fetching Pokemon (Pokedex Number: {}): {}",
                    requestId, pokedexNumber, ex.getMessage(), ex);
            throw new PokeApiException("Failed to fetch PokeAPI.", ex);
        }
    }
}
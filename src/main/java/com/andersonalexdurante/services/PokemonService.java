package com.andersonalexdurante.services;

import com.andersonalexdurante.dto.PokemonDTO;
import com.andersonalexdurante.exceptions.PokemonException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.StreamSupport;

@ApplicationScoped
public class PokemonService {

    private static final Logger LOGGER = LoggerFactory.getLogger(PokemonService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    @ConfigProperty(name = "POKEAPI_URL")
    String pokeApiUrl;

    public PokemonDTO getPokemonData(String requestId, int pokedexNumber) {
        String pokemonUrl = this.pokeApiUrl + pokedexNumber;

        LOGGER.info("[{}] Fetching Pokemon from URL: {}", requestId, pokemonUrl);

        try {
            HttpResponse<String> response = HttpClient.newHttpClient()
                    .send(HttpRequest.newBuilder()
                            .uri(URI.create(pokemonUrl))
                            .GET()
                            .build(), HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JsonNode rootNode = this.objectMapper.readTree(response.body());
                String name = rootNode.get("species").get("name").asText().toUpperCase();
                List<String> types = extractTypes(rootNode);

                String speciesUrl = rootNode.get("species").get("url").asText();
                JsonNode speciesData = fetchJsonFromUrl(speciesUrl);
                List<String> descriptions = extractPokemonDescriptions(speciesData);
                String habitat = extractHabitat(speciesData);

                LOGGER.info("[{}] Successfully fetched Pokemon: {} (Pokedex Number: {})",
                        requestId, name, pokedexNumber);

                return new PokemonDTO(pokedexNumber, name, types, descriptions, habitat);
            } else {
                throw new PokemonException("Failed to fetch PokeAPI. HTTP status: " + response.statusCode());
            }
        } catch (Exception ex) {
            throw new PokemonException("Failed to fetch PokeAPI.", ex);
        }
    }

    public int getPokedexNumber(String dollarExchangeRate) {
        int pokedexNumber = Integer.parseInt(dollarExchangeRate.split(",")[1]);
        LOGGER.debug("Calculated Pokedex number: #{}. Dollar Rate: ${}", pokedexNumber, dollarExchangeRate);
        return pokedexNumber;
    }

    private List<String> extractTypes(JsonNode rootNode) {
        List<String> types = new ArrayList<>();
        rootNode.get("types").forEach(typeNode -> types.add(typeNode.get("type").get("name").asText()));
        return types;
    }

    private List<String> extractPokemonDescriptions(JsonNode speciesData) {
        List<String> descriptions = StreamSupport.stream(speciesData.get("flavor_text_entries").spliterator(), false)
                .filter(entry -> "en".equals(entry.get("language").get("name").asText()))
                .map(entry -> entry.get("flavor_text").asText().replace("\n", " ").replace("\f", " "))
                .toList();

        return descriptions.isEmpty() ? List.of("No description available.") : descriptions;
    }

    private String extractHabitat(JsonNode speciesData) {
        if (speciesData.has("habitat") && !speciesData.get("habitat").isNull()) {
            return speciesData.get("habitat").get("name").asText();
        }
        return "unknown";
    }

    private JsonNode fetchJsonFromUrl(String url) {
        try {
            HttpResponse<String> response = HttpClient.newHttpClient()
                    .send(HttpRequest.newBuilder().uri(URI.create(url)).GET().build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                return objectMapper.readTree(response.body());
            } else {
                LOGGER.error("Failed to fetch JSON from URL: {} | HTTP Status: {}", url, response.statusCode());
            }
        } catch (Exception ex) {
            LOGGER.error("Exception while fetching JSON from URL: {} | Error: {}", url, ex.getMessage(), ex);
        }
        return this.objectMapper.createObjectNode();
    }
}

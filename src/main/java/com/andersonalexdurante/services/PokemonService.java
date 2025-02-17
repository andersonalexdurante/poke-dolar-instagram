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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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

            int statusCode = response.statusCode();
            if (statusCode == 200) {
                JsonNode rootNode = this.objectMapper.readTree(response.body());

                String name = rootNode.get("species").get("name").asText().toUpperCase();
                List<String> types = this.extractTypes(rootNode);
                Map<String, String> abilities = this.extractAbilitiesWithDescriptions(rootNode);

                String speciesUrl = rootNode.get("species").get("url").asText();
                String description = this.fetchPokemonDescription(speciesUrl);

                LOGGER.info("[{}] Successfully fetched Pokemon: {} (Pokedex Number: {})", requestId, name, pokedexNumber);
                return new PokemonDTO(pokedexNumber, name, types, abilities, description);
            } else {
                LOGGER.error("[{}] Failed to fetch Pokemon. HTTP Status: {} | URL: {}", requestId, statusCode, pokemonUrl);
                throw new PokemonException("Failed to fetch PokeAPI. HTTP status: " + statusCode);
            }
        } catch (Exception ex) {
            LOGGER.error("[{}] Exception occurred while fetching Pokemon (Pokedex Number: {}): {}", requestId,
                    pokedexNumber, ex.getMessage(), ex);
            throw new PokemonException("Failed to fetch PokeAPI.", ex);
        }
    }

    private List<String> extractTypes(JsonNode rootNode) {
        List<String> types = new ArrayList<>();
        rootNode.get("types").forEach(typeNode -> types.add(typeNode.get("type").get("name").asText()));
        return types;
    }

    private Map<String, String> extractAbilitiesWithDescriptions(JsonNode rootNode) {
        Map<String, String> abilities = new LinkedHashMap<>();
        rootNode.get("abilities").forEach(abilityNode -> {
            String abilityName = abilityNode.get("ability").get("name").asText();
            String abilityUrl = abilityNode.get("ability").get("url").asText();
            String abilityDescription = this.fetchAbilityDescription(abilityUrl);
            abilities.put(abilityName, abilityDescription);
        });
        return abilities;
    }

    private String fetchAbilityDescription(String abilityUrl) {
        try {
            HttpResponse<String> response = HttpClient.newHttpClient()
                    .send(HttpRequest.newBuilder()
                            .uri(URI.create(abilityUrl))
                            .GET()
                            .build(), HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JsonNode abilityNode = this.objectMapper.readTree(response.body());

                for (JsonNode entry : abilityNode.get("flavor_text_entries")) {
                    if (entry.get("language").get("name").asText().equals("en")) {
                        return entry.get("flavor_text").asText()
                                .replace("\n", " ")
                                .replace("\f", " ");
                    }
                }
            } else {
                LOGGER.error("Failed to fetch ability description. HTTP Status: {}", response.statusCode());
            }
        } catch (Exception ex) {
            LOGGER.error("Exception while fetching ability description: {}", ex.getMessage(), ex);
        }
        return "No description available.";
    }

    private String fetchPokemonDescription(String speciesUrl) {
        try {
            HttpResponse<String> response = HttpClient.newHttpClient()
                    .send(HttpRequest.newBuilder()
                            .uri(URI.create(speciesUrl))
                            .GET()
                            .build(), HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JsonNode speciesNode = objectMapper.readTree(response.body());

                String longestDescription = "No description available.";
                int maxLength = 0;

                // Percorre todas as descrições em inglês e seleciona a mais longa
                for (JsonNode entry : speciesNode.get("flavor_text_entries")) {
                    if (entry.get("language").get("name").asText().equals("en")) {
                        String description = entry.get("flavor_text").asText()
                                .replace("\n", " ") // Remove quebras de linha
                                .replace("\f", " "); // Remove caracteres especiais

                        if (description.length() > maxLength) {
                            maxLength = description.length();
                            longestDescription = description;
                        }
                    }
                }

                return longestDescription;
            } else {
                LOGGER.error("Failed to fetch Pokemon species. HTTP Status: {}", response.statusCode());
            }
        } catch (Exception ex) {
            LOGGER.error("Exception while fetching Pokemon species: {}", ex.getMessage(), ex);
        }
        return "No description available.";
    }

}

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

            if (response.statusCode() == 200) {
                JsonNode rootNode = this.objectMapper.readTree(response.body());
                String name = rootNode.get("species").get("name").asText().toUpperCase();
                List<String> types = extractTypes(rootNode);
                Map<String, String> abilities = extractAbilitiesWithDescriptions(rootNode);

                String speciesUrl = rootNode.get("species").get("url").asText();
                JsonNode speciesData = fetchJsonFromUrl(speciesUrl);
                String description = extractPokemonDescription(speciesData);
                boolean isFinalEvolution = checkIfFinalEvolution(speciesData);

                LOGGER.info("[{}] Successfully fetched Pokemon: {} (Pokedex Number: {} | Final Evolution: {})",
                        requestId, name, pokedexNumber, isFinalEvolution);

                return new PokemonDTO(pokedexNumber, name, types, abilities, description, isFinalEvolution);
            } else {
                throw new PokemonException("Failed to fetch PokeAPI. HTTP status: " + response.statusCode());
            }
        } catch (Exception ex) {
            throw new PokemonException("Failed to fetch PokeAPI.", ex);
        }
    }

    public int getPokedexNumber(String dollarExchangeRate) {
        int pokedexNumber = Integer.parseInt(dollarExchangeRate.replace(",", ""));
        LOGGER.debug("Calculated Pokedex number: #{}. Dollar Rate: ${}", pokedexNumber, dollarExchangeRate);
        return pokedexNumber;
    }

    private boolean checkIfFinalEvolution(JsonNode speciesData) {
        try {
            String evolutionChainUrl = speciesData.get("evolution_chain").get("url").asText();
            JsonNode evolutionChain = fetchJsonFromUrl(evolutionChainUrl).get("chain");
            return isLastEvolutionStage(evolutionChain, speciesData.get("name").asText());
        } catch (Exception ex) {
            LOGGER.error("Error fetching evolution chain: {}", ex.getMessage(), ex);
            return false;
        }
    }

    private boolean isLastEvolutionStage(JsonNode evolutionNode, String currentPokemonName) {
        if (evolutionNode.get("species").get("name").asText().equals(currentPokemonName)) {
            return evolutionNode.get("evolves_to").isEmpty();
        }
        for (JsonNode nextStage : evolutionNode.get("evolves_to")) {
            if (isLastEvolutionStage(nextStage, currentPokemonName)) {
                return true;
            }
        }
        return false;
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
            String abilityDescription = fetchAbilityDescription(abilityUrl);
            abilities.put(abilityName, abilityDescription);
        });
        return abilities;
    }

    private String fetchAbilityDescription(String abilityUrl) {
        JsonNode abilityNode = fetchJsonFromUrl(abilityUrl);
        for (JsonNode entry : abilityNode.get("flavor_text_entries")) {
            if (entry.get("language").get("name").asText().equals("en")) {
                return entry.get("flavor_text").asText().replace("\n", " ").replace("\f", " ");
            }
        }
        return "No description available.";
    }

    private String extractPokemonDescription(JsonNode speciesData) {
        StringBuilder descriptionBuilder = new StringBuilder();

        for (JsonNode entry : speciesData.get("flavor_text_entries")) {
            if ("en".equals(entry.get("language").get("name").asText())) {
                if (!descriptionBuilder.isEmpty()) {
                    descriptionBuilder.append(" - "); // Add separator
                }
                descriptionBuilder.append(entry.get("flavor_text").asText().replace("\n", " ").replace("\f", " "));
            }
        }

        return !descriptionBuilder.isEmpty() ? descriptionBuilder.toString() : "No description available.";
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

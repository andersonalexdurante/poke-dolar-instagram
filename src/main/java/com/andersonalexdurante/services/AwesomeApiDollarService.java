package com.andersonalexdurante.services;

import com.andersonalexdurante.exceptions.DollarException;
import com.andersonalexdurante.interfaces.IDollarService;
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

@ApplicationScoped
public class AwesomeApiDollarService implements IDollarService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AwesomeApiDollarService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    @ConfigProperty(name = "AWESOMEAPI_DOLLAR_URL")
    String dollarApiUrl;

    @Override
    public String getDollarExchangeRate(String requestId) {
        LOGGER.info("[{}] [START] Fetching the dollar exchange rate from AwesomeAPI", requestId);

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(this.dollarApiUrl))
                    .GET()
                    .build();

            HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JsonNode rootNode = this.objectMapper.readTree(response.body());
                String exchangeRate = rootNode.path("USD").path("bid").asText();
                String formattedBid = exchangeRate.substring(0, 4).replace('.', ',');

                LOGGER.info("[{}] [SUCCESS] AwesomeAPI Dollar exchange rate fetched: BRL ${}", requestId, formattedBid);
                return formattedBid;
            }

            LOGGER.warn("[{}] [WARN] Failed to fetch dollar exchange rate. HTTP status: {}", requestId, response.statusCode());
            throw new DollarException("Failed to fetch Dollar Exchange Rate. HTTP status: " + response.statusCode());

        } catch (Exception e) {
            LOGGER.error("[{}] [ERROR] Exception while fetching Dollar Exchange Rate", requestId, e);
            throw new DollarException("Error trying to fetch Dollar Exchange Rate.", e);
        }
    }
}

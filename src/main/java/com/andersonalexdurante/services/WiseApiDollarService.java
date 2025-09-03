package com.andersonalexdurante.services;

import com.andersonalexdurante.exceptions.DollarException;
import com.andersonalexdurante.interfaces.IDollarService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

@ApplicationScoped
public class WiseApiDollarService implements IDollarService {

    private static final Logger LOGGER = LoggerFactory.getLogger(WiseApiDollarService.class);
    private static final String WISE_API_TOKEN_PARAMETER = "wise_api_token";
    private final ObjectMapper objectMapper = new ObjectMapper();

    @ConfigProperty(name = "WISEAPI_DOLLAR_URL")
    String dollarApiUrl;

    @Inject
    SsmService ssmService;

    @Override
    public String getDollarExchangeRate(String requestId) {
        LOGGER.info("[{}] [START] Fetching the dollar exchange rate from Wise API", requestId);

        try {
            String apiToken = this.ssmService.getStringParameterWithDecryption(requestId, WISE_API_TOKEN_PARAMETER);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(this.dollarApiUrl))
                    .header("Authorization", "Bearer " + apiToken)
                    .GET()
                    .build();

            HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JsonNode rootNode = this.objectMapper.readTree(response.body());
                String exchangeRate = rootNode.get(0).path("rate").asText();
                String formattedBid = exchangeRate.substring(0, 5).replace('.', ',');

                LOGGER.info("[{}] [SUCCESS] Wise API Dollar exchange rate fetched: BRL ${}", requestId, formattedBid);
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

package com.andersonalexdurante.services;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.time.Instant;
import java.util.*;

@ApplicationScoped
public class DynamoDBService {

    private static final String POKE_DOLAR_POSTS_TABLE = "PokeDolarPosts";
    private static final Logger LOGGER = LoggerFactory.getLogger(DynamoDBService.class);

    @Inject
    DynamoDbClient dynamoDbClient;

    public void savePost(String requestId, String pokemon, String dollarValue, String caption) {
        Map<String, AttributeValue> item = new HashMap<>();
        item.put("context_id", AttributeValue.builder().s("posts").build());
        item.put("timestamp", AttributeValue.builder().s(Instant.now().toString()).build());
        item.put("pokemon", AttributeValue.builder().s(pokemon).build());
        item.put("dollar_rate", AttributeValue.builder().s(String.valueOf(dollarValue)).build());
        item.put("caption", AttributeValue.builder().s(caption).build());

        PutItemRequest request = PutItemRequest.builder()
                .tableName(POKE_DOLAR_POSTS_TABLE)
                .item(item)
                .build();

        try {
            this.dynamoDbClient.putItem(request);
            LOGGER.info("[{}] Post saved successfully.", requestId);
        } catch (Exception e) {
            LOGGER.error("[{}] Error saving post: {}", requestId, e.getMessage(), e);
        }
    }

    public Optional<String> getLastDollarRate(String requestId) {
        LOGGER.info("[{}] Fetching last dollar rate.", requestId);

        QueryRequest queryRequest = QueryRequest.builder()
                .tableName(POKE_DOLAR_POSTS_TABLE)
                .keyConditionExpression("context_id = :context")
                .expressionAttributeValues(Map.of(":context", AttributeValue.builder().s("posts").build()))
                .projectionExpression("dollar_rate")
                .limit(1)
                .scanIndexForward(false)
                .build();

        try {
            QueryResponse response = this.dynamoDbClient.query(queryRequest);
            if (!response.items().isEmpty()) {
                String dollarRate = response.items().getFirst().get("dollar_rate").s();
                LOGGER.info("[{}] Last dollar rate: {}", requestId, dollarRate);
                return Optional.ofNullable(dollarRate);
            } else {
                LOGGER.warn("[{}] No dollar rate found.", requestId);
                return Optional.empty();
            }
        } catch (Exception e) {
            LOGGER.error("[{}] Error fetching last dollar rate: {}", requestId, e.getMessage(), e);
            return Optional.empty();
        }
    }
}

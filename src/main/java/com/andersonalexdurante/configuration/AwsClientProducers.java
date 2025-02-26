package com.andersonalexdurante.configuration;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

@ApplicationScoped
public class AwsClientProducers {

    @Produces
    @ApplicationScoped
    public BedrockRuntimeClient bedrockRuntimeClient() {
        return BedrockRuntimeClient.builder()
                .region(Region.US_EAST_2)
                .build();
    }

    @Produces
    public DynamoDbClient dynamoDbClient() {
        return DynamoDbClient.builder()
                .region(Region.US_EAST_2)
                .build();
    }

}

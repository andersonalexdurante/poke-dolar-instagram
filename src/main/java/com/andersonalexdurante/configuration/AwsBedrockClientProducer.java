package com.andersonalexdurante.configuration;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;

@ApplicationScoped
public class AwsBedrockClientProducer {

    @Produces
    @ApplicationScoped
    public BedrockRuntimeClient bedrockRuntimeClient() {
        return BedrockRuntimeClient.builder()
                .region(Region.of("us-east-2"))
                .build();
    }
}

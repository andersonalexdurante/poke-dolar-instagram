package com.andersonalexdurante.services;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.ssm.SsmClient;
import software.amazon.awssdk.services.ssm.model.GetParameterRequest;
import software.amazon.awssdk.services.ssm.model.GetParameterResponse;
import software.amazon.awssdk.services.ssm.model.SsmException;

@ApplicationScoped
public class SsmService {

    private static final Logger LOGGER = LoggerFactory.getLogger(SsmService.class);

    @Inject
    SsmClient ssmClient;

    public String getStringParameterWithDecryption(String requestId, String parameterName) {
        LOGGER.info("[{}] Getting parameter {} from AWS Parameter Store", requestId, parameterName);
        try {
            GetParameterRequest request = GetParameterRequest.builder()
                    .name(parameterName)
                    .withDecryption(true)
                    .build();

            GetParameterResponse response = this.ssmClient.getParameter(request);
            LOGGER.info("[{}] Parameter recovered!", requestId);
            return response.parameter().value();
        } catch (SsmException e) {
            LOGGER.error("[{}] Error while getting parameter from AWS Parameter Store!", requestId, e);
            throw e;
        }
    }

}

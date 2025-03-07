package com.andersonalexdurante.configuration;

import com.andersonalexdurante.interfaces.IDollarService;
import com.andersonalexdurante.services.AwesomeApiDollarService;
import com.andersonalexdurante.services.WiseApiDollarService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.ws.rs.Produces;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class DollarServiceProducer {

    @ConfigProperty(name = "DOLLAR_SERVICE_TYPE", defaultValue = "AWESOME")
    String dollarServiceType;

    @Inject
    AwesomeApiDollarService awesomeApiDollarService;

    @Inject
    WiseApiDollarService wiseApiDollarService;

    @Produces
    @Named("dollarService")
    public IDollarService getDollarService() {
        if ("WISE".equalsIgnoreCase(this.dollarServiceType)) {
            return this.wiseApiDollarService;
        }
        return this.awesomeApiDollarService;
    }
}
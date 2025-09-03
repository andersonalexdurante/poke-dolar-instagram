package com.andersonalexdurante.interfaces;

import com.andersonalexdurante.dto.DollarVariationDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;

public interface IDollarService {

    Logger LOGGER = LoggerFactory.getLogger(IDollarService.class);

    String getDollarExchangeRate(String requestId);

    default boolean dollarRateChanged(Optional<String> lastDollarRate, String dollarExchangeRate) {
        if (lastDollarRate.isEmpty()) {
            return true;
        }

        double lastRate = Double.parseDouble(lastDollarRate.get().replace(",", "."));
        double currentRate = Double.parseDouble(dollarExchangeRate.replace(",", "."));

        boolean changed = lastRate != currentRate;
        LOGGER.debug("Checking if Dollar Rate changed: {}", changed);
        return changed;
    }

    default DollarVariationDTO getDollarVariation(String requestId, String lastDollarExchangeRate,
                                                  String dollarExchangeRate) {
        BigDecimal lastDollarRate = new BigDecimal(lastDollarExchangeRate.replace(",", "."));
        BigDecimal newDollarRate = new BigDecimal(dollarExchangeRate.replace(",", "."));
        BigDecimal variation = newDollarRate.subtract(lastDollarRate).abs();
        BigDecimal variationInCents = variation.multiply(new BigDecimal("100")).setScale(1, RoundingMode.HALF_UP);

        if (newDollarRate.compareTo(lastDollarRate) > 0) {
            LOGGER.info("[{}] BRL to USD rose: {} -> {}", requestId, lastDollarRate, newDollarRate);
            return new DollarVariationDTO(variationInCents, true);
        }
        LOGGER.info("[{}] BRL to USD fell: {} -> {}", requestId, lastDollarRate, newDollarRate);
        return new DollarVariationDTO(variationInCents, false);
    }


}

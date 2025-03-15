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
        boolean dollarRateChanged = lastDollarRate.isEmpty() ||
                lastDollarRate.map(s -> !s.equals(dollarExchangeRate)).orElse(true);
        LOGGER.debug("Checking if Dollar Rate changed: {}", dollarRateChanged);
        return dollarRateChanged;
    }

    default DollarVariationDTO getDollarVariation(String requestId, String lastDollarExchangeRate,
                                                  String dollarExchangeRate) {
        double lastDollarRate = Double.parseDouble(lastDollarExchangeRate.replace(",", "."));
        double newDollarRate = Double.parseDouble(dollarExchangeRate.replace(",", "."));
        double variation = Math.abs(newDollarRate - lastDollarRate);
        BigDecimal roundedVariation = new BigDecimal(variation).setScale(2, RoundingMode.HALF_UP);
        if (newDollarRate > lastDollarRate) {
            LOGGER.info("[{}] BRL to USD rose: {} -> {}", requestId, lastDollarRate, newDollarRate);
            return new DollarVariationDTO(roundedVariation, true);
        }
        LOGGER.info("[{}] BRL to USD fell: {} -> {}", requestId, lastDollarRate, newDollarRate);
        return new DollarVariationDTO(roundedVariation, false);
    }
}

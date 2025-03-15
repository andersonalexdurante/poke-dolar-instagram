package com.andersonalexdurante.dto;

import java.math.BigDecimal;

public record DollarVariationDTO(BigDecimal variation, boolean isUp) {
}

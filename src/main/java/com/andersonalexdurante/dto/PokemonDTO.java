package com.andersonalexdurante.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;

import java.util.List;
import java.util.Map;

@RegisterForReflection
public record PokemonDTO(
        int number,
        String name,
        List<String> types,
        Map<String, String> abilities,
        String description) {
}
package com.andersonalexdurante.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public record PokemonDTO(int pokedexNumber, String pokemonName) {
}

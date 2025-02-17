package com.andersonalexdurante.exceptions;

public class PokemonException extends RuntimeException {

    public PokemonException(String msg, Throwable cause) {
        super(msg, cause);
    }

    public PokemonException(String msg) {
        super(msg);
    }
}

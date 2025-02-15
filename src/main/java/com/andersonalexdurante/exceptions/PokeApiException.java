package com.andersonalexdurante.exceptions;

public class PokeApiException extends RuntimeException {

    public PokeApiException(String msg, Throwable cause) {
        super(msg, cause);
    }

    public PokeApiException(String msg) {
        super(msg);
    }
}

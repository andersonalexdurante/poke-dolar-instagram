package com.andersonalexdurante.exceptions;

public class InstagramApiException extends RuntimeException{

    public InstagramApiException(String msg, Throwable cause) {
        super(msg, cause);
    }

    public InstagramApiException(String msg) {
        super(msg);
    }
}

package com.andersonalexdurante.exceptions;

public class DollarException extends RuntimeException{

    public DollarException(String msg, Throwable cause) {
        super(msg, cause);
    }

    public DollarException(String msg) {
        super(msg);
    }
}

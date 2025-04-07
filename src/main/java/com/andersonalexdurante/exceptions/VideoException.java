package com.andersonalexdurante.exceptions;

public class VideoException extends RuntimeException{

    public VideoException(String msg, Throwable cause) {
        super(msg, cause);
    }

    public VideoException(String msg) {
        super(msg);
    }
}

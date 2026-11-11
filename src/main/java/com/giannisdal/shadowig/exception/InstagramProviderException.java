package com.giannisdal.shadowig.exception;

public class InstagramProviderException extends RuntimeException {

    public InstagramProviderException(String message, Throwable cause) {
        super(message, cause);
    }

    public InstagramProviderException(String message) {
        super(message);
    }
}

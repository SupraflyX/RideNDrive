package com.routeshare.exception;

// the maps api said something other than OK, or we could not reach it at all
public class MapApiException extends RuntimeException {
    public MapApiException(String message) {
        super(message);
    }
}

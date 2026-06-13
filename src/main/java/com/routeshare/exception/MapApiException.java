package com.routeshare.exception;

/**
 * Exception thrown when the Google Maps API returns status codes other than OK,
 * such as ZERO_RESULTS or NOT_FOUND, or experiences API credentials/network failures.
 */
public class MapApiException extends RuntimeException {
    public MapApiException(String message) {
        super(message);
    }
}

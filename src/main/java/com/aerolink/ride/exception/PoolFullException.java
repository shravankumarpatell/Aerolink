package com.aerolink.ride.exception;

public class PoolFullException extends RuntimeException {
    public PoolFullException(String message) {
        super(message);
    }
}

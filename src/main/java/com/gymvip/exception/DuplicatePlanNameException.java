package com.gymvip.exception;

public class DuplicatePlanNameException extends RuntimeException {
    public DuplicatePlanNameException(String message) {
        super(message);
    }
}

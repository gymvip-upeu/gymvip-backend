package com.gymvip.plans.domain.exception;

public class InvalidPlanException extends RuntimeException {
    public InvalidPlanException(String message) {
        super(message);
    }
}

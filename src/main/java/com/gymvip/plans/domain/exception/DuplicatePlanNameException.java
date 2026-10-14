package com.gymvip.plans.domain.exception;

public class DuplicatePlanNameException extends RuntimeException {
    public DuplicatePlanNameException(String message) {
        super(message);
    }
}

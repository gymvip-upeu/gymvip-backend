package com.gymvip.saas.controller;

import com.gymvip.dto.response.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.UUID;

/**
 * Normaliza excepciones de dominio y consultas JDBC en controladores SaaS B2B2C
 * sin alterar contratos de controladores heredados.
 */
@RestControllerAdvice(basePackages = "com.gymvip.saas.controller")
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SaasExceptionAdvice {

    @ExceptionHandler(InvalidDataAccessApiUsageException.class)
    public ResponseEntity<ErrorResponse> handleWrappedIllegalArgument(InvalidDataAccessApiUsageException ex,
                                                                      HttpServletRequest request) {
        Throwable cause = ex.getCause();
        if (cause instanceof IllegalArgumentException) {
            return ResponseEntity.badRequest().body(new ErrorResponse(
                    HttpStatus.BAD_REQUEST.value(),
                    HttpStatus.BAD_REQUEST.getReasonPhrase(),
                    "INVALID_ARGUMENT",
                    cause.getMessage(),
                    request.getRequestURI(),
                    correlationId(request)
            ));
        }
        throw ex;
    }

    @ExceptionHandler(EmptyResultDataAccessException.class)
    public ResponseEntity<ErrorResponse> handleMissingRecord(EmptyResultDataAccessException ex,
                                                             HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse(
                HttpStatus.NOT_FOUND.value(),
                HttpStatus.NOT_FOUND.getReasonPhrase(),
                "RESOURCE_NOT_FOUND",
                "Recurso SaaS no encontrado.",
                request.getRequestURI(),
                correlationId(request)
        ));
    }

    private String correlationId(HttpServletRequest request) {
        String id = request.getHeader("X-Correlation-Id");
        return (id == null || id.isBlank()) ? UUID.randomUUID().toString() : id;
    }
}

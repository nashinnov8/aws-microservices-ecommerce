package com.ecommerce.authservice.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import response.ApiResponse;

import java.util.UUID;

@Slf4j
@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler
    public ResponseEntity<ApiResponse<Object>> handleUserAlreadyExists(UserAlreadyExistsException ex) {
        log.warn("User already exists: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(ApiResponse.error(String.valueOf(HttpStatus.CONFLICT.value()), "User already exists"));
    }

    @ExceptionHandler
    public ResponseEntity<ApiResponse<Object>> handleUserNotExists(UserNotExistException ex) {
        log.warn("User does not exist: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(String.valueOf(HttpStatus.NOT_FOUND.value()), "User does not exist"));
    }

    @ExceptionHandler
    public ResponseEntity<ApiResponse<Object>> handleGenericException(Exception ex) {
        log.warn("An error occurred: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(String.valueOf(HttpStatus.INTERNAL_SERVER_ERROR.value()), "An internal server error occurred"));
    }
 }

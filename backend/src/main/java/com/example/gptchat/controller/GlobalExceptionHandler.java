package com.example.gptchat.controller;

import com.example.gptchat.dto.ErrorResponse;
import com.example.gptchat.service.ChatModelException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log =
        LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(
        MethodArgumentNotValidException exception) {

        String detail = exception.getBindingResult()
            .getFieldErrors()
            .stream()
            .findFirst()
            .map(error -> error.getField() + " " + error.getDefaultMessage())
            .orElse("Request validation failed.");

        return build(HttpStatus.BAD_REQUEST, detail);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(
        IllegalArgumentException exception) {

        return build(HttpStatus.BAD_REQUEST, exception.getMessage());
    }

    @ExceptionHandler(ChatModelException.class)
    public ResponseEntity<ErrorResponse> handleModelFailure(
        ChatModelException exception) {

        /*
         * 細節留在 server log，前端只拿到一句概括訊息
         */
        log.error("Chat model call failed", exception);

        return build(
            HttpStatus.BAD_GATEWAY,
            "Upstream chat model is unavailable. Please try again."
        );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(
        Exception exception) {

        log.error("Unhandled exception", exception);

        return build(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "Unexpected server error."
        );
    }

    private ResponseEntity<ErrorResponse> build(
        HttpStatus status, String message) {

        return ResponseEntity
            .status(status)
            .body(new ErrorResponse(status.value(), message));
    }
}

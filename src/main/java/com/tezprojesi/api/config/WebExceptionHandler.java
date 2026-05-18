package com.tezprojesi.api.config;

import com.tezprojesi.api.dto.ApiErrorDto;
import com.tezprojesi.api.exception.BadRequestException;
import com.tezprojesi.api.exception.NotFoundException;
import com.tezprojesi.api.exception.ServiceException;
import java.time.Instant;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestControllerAdvice
public class WebExceptionHandler {
  private static final Logger logger = LoggerFactory.getLogger(WebExceptionHandler.class);
  
  @Value("${spring.profiles.active:production}")
  private String profile;

  @ExceptionHandler(NotFoundException.class)
  public ResponseEntity<ApiErrorDto> handleNotFound(NotFoundException ex, HttpServletRequest request) {
    return buildError(HttpStatus.NOT_FOUND, ex.getMessage(), request.getRequestURI());
  }

  @ExceptionHandler(BadRequestException.class)
  public ResponseEntity<ApiErrorDto> handleBadRequest(BadRequestException ex, HttpServletRequest request) {
    return buildError(HttpStatus.BAD_REQUEST, ex.getMessage(), request.getRequestURI());
  }

  @ExceptionHandler(ServiceException.class)
  public ResponseEntity<ApiErrorDto> handleServiceException(ServiceException ex, HttpServletRequest request) {
    return buildError(HttpStatus.INTERNAL_SERVER_ERROR, ex.getMessage(), request.getRequestURI());
  }

  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<ApiErrorDto> handleIllegalArgument(IllegalArgumentException ex, HttpServletRequest request) {
    return buildError(HttpStatus.BAD_REQUEST, ex.getMessage(), request.getRequestURI());
  }

  @ExceptionHandler(IllegalStateException.class)
  public ResponseEntity<ApiErrorDto> handleIllegalState(IllegalStateException ex, HttpServletRequest request) {
    return buildError(HttpStatus.CONFLICT, ex.getMessage(), request.getRequestURI());
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiErrorDto> handleException(Exception ex, HttpServletRequest request) {
    logger.error("Unexpected error in request to {}", request.getRequestURI(), ex);
    
    String message;
    if (isDevelopment()) {
      message = ex.getMessage() != null ? ex.getMessage() : "Unexpected exception";
    } else {
      message = "An unexpected error occurred. Please contact support with error code: " + System.nanoTime();
    }
    
    return buildError(HttpStatus.INTERNAL_SERVER_ERROR, message, request.getRequestURI(), ex);
  }

  private ResponseEntity<ApiErrorDto> buildError(HttpStatus status, String message, String path) {
    return buildError(status, message, path, null);
  }

  private ResponseEntity<ApiErrorDto> buildError(HttpStatus status, String message, String path, Exception exception) {
    ApiErrorDto dto = new ApiErrorDto();
    dto.setStatus(status.value());
    dto.setError(status.getReasonPhrase());
    dto.setMessage(message);
    dto.setPath(path);
    dto.setTimestamp(Instant.now());
    
    // Add stack trace details only in development
    if (isDevelopment() && exception != null) {
      logger.debug("Stack trace for error at {}", path, exception);
    }
    
    return new ResponseEntity<>(dto, status);
  }
  
  private boolean isDevelopment() {
    return profile != null && (profile.contains("dev") || profile.contains("local"));
  }
}

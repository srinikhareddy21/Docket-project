package com.docket.web;

import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class ApiExceptionHandler {
  private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

  @ExceptionHandler(ApiException.class)
  ResponseEntity<ApiError> handleApi(ApiException e) {
    return ResponseEntity.status(e.status()).body(new ApiError(e.getMessage(), e.fieldErrors()));
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException e) {
    Map<String, String> fields = new LinkedHashMap<>();
    e.getBindingResult().getFieldErrors().forEach(f -> fields.putIfAbsent(f.getField(), f.getDefaultMessage()));
    return ResponseEntity.badRequest().body(new ApiError("Some details are invalid. Check the form and try again.", fields));
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  ResponseEntity<ApiError> handleUnreadable(HttpMessageNotReadableException e) {
    return ResponseEntity.badRequest().body(ApiError.of("The request could not be read."));
  }

  @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
  ResponseEntity<ApiError> handleMethod(HttpRequestMethodNotSupportedException e) {
    return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(ApiError.of("That action is not supported."));
  }

  @ExceptionHandler(NoResourceFoundException.class)
  ResponseEntity<ApiError> handleNotFound(NoResourceFoundException e) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError.of("We could not find what you asked for."));
  }

  @ExceptionHandler(MaxUploadSizeExceededException.class)
  ResponseEntity<ApiError> handleTooBig(MaxUploadSizeExceededException e) {
    return ResponseEntity.status(HttpStatus.valueOf(413)).body(ApiError.of("Files must be 5 MB or smaller."));
  }

  @ExceptionHandler(MissingServletRequestPartException.class)
  ResponseEntity<ApiError> handleMissingPart(MissingServletRequestPartException e) {
    return ResponseEntity.badRequest().body(ApiError.of("Choose a file to upload."));
  }

  @ExceptionHandler(org.springframework.web.method.annotation.HandlerMethodValidationException.class)
  ResponseEntity<ApiError> handleMethodValidation(org.springframework.web.method.annotation.HandlerMethodValidationException e) {
    return ResponseEntity.badRequest().body(ApiError.of("Some details are invalid. Check the form and try again."));
  }

  @ExceptionHandler(org.springframework.web.bind.MissingServletRequestParameterException.class)
  ResponseEntity<ApiError> handleMissingParam(org.springframework.web.bind.MissingServletRequestParameterException e) {
    return ResponseEntity.badRequest().body(ApiError.of("Some details are missing. Check the form and try again."));
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<ApiError> handleOther(Exception e) {
    log.error("Unhandled error", e);   // details stay in the server log, never in the response
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiError.of("Something went wrong on our side. Try again shortly."));
  }
}

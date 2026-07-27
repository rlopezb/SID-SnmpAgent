package es.vodafone.sim.snmp.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.io.IOException;

@ControllerAdvice
public class ApiExceptionHandler {

  @ExceptionHandler(IllegalArgumentException.class)
  @ResponseStatus(HttpStatus.NOT_FOUND)
  public ErrorDto handleNotFound(IllegalArgumentException e) {
    return new ErrorDto("NOT_FOUND", e.getMessage());
  }

  @ExceptionHandler(IllegalStateException.class)
  @ResponseStatus(HttpStatus.CONFLICT)
  public ErrorDto handleConflict(IllegalStateException e) {
    return new ErrorDto("CONFLICT", e.getMessage());
  }

  @ExceptionHandler(IOException.class)
  @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
  public ErrorDto handleIo(IOException e) {
    return new ErrorDto("IO_ERROR", "Error al acceder al recurso: " + e.getMessage());
  }

  @ExceptionHandler(Exception.class)
  @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
  public ErrorDto handleGeneric(Exception e) {
    return new ErrorDto("ERROR", "Error interno");
  }

  public record ErrorDto(String code, String message) {}
}
package org.folio.rest.exception;

import java.util.List;

public class UnprocessableEntityException extends RuntimeException {

  private final List<UnprocessableEntityMessage> errors;

  public UnprocessableEntityException(List<UnprocessableEntityMessage> errors) {
    this.errors = errors;
  }

  public List<UnprocessableEntityMessage> getErrors() {
    return errors;
  }
}

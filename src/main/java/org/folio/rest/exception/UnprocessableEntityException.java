package org.folio.rest.exception;

import java.util.List;

/**

 * Exception indicating that request can not be performed due to invalid request entity
 */
public class UnprocessableEntityException extends RuntimeException {

  private final List<UnprocessableEntityMessage> errors;

  /**
   * Constructor

   * @param errors list of exception reasons
   */
  public UnprocessableEntityException(List<UnprocessableEntityMessage> errors) {
    this.errors = errors;
  }

  public List<UnprocessableEntityMessage> getErrors() {
    return errors;
  }
}

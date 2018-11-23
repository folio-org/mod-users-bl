package org.folio.rest.exception;

import java.io.Serializable;

/**
 * Model describing reason of {@link UnprocessableEntityException}
 */
public class UnprocessableEntityMessage implements Serializable {

  private String code;
  private String message;

  /**
   * Constructor
   *
   * @param code    error code
   * @param message human readable message
   */
  public UnprocessableEntityMessage(String code, String message) {
    this.code = code;
    this.message = message;
  }

  public String getCode() {
    return code;
  }

  public String getMessage() {
    return message;
  }
}

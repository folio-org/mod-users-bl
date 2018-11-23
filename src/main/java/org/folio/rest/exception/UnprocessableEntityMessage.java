package org.folio.rest.exception;

import org.apache.commons.lang3.builder.ToStringBuilder;

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

  @Override
  public String toString() {
    return new ToStringBuilder(this)
      .append("code", code)
      .append("message", message)
      .toString();
  }
}

package org.folio.rest.exception;

import java.io.Serializable;

public class UnprocessableEntityMessage implements Serializable {

  private String code;
  private String message;

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

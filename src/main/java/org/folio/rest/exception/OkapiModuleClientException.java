package org.folio.rest.exception;

public class OkapiModuleClientException extends RuntimeException {

  public OkapiModuleClientException() {
    super();
  }

  public OkapiModuleClientException(String message) {
    super(message);
  }

  public OkapiModuleClientException(String message, Throwable cause) {
    super(message, cause);
  }

  public OkapiModuleClientException(Throwable cause) {
    super(cause);
  }
}

package org.folio.rest.exception;

import java.util.List;

/**

 * Exception indicating that request can not be performed due to multiple entities with the same identifier
 */
public class MultipleEntityException extends UnprocessableEntityException {

  /**
   * Constructor

   * @param errors list of exception reasons
   */
  public MultipleEntityException(List<UnprocessableEntityMessage> errors) {
    super(errors);
  }
}

package org.folio.rest.util;

import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.apache.http.HttpStatus;
import org.folio.rest.exception.UnprocessableEntityException;
import org.folio.rest.jaxrs.model.Error;
import org.folio.rest.jaxrs.model.Errors;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.List;
import java.util.stream.Collectors;

public final class ExceptionHelper {

  private static final Logger LOG = LoggerFactory.getLogger("mod-users-bl");

  private ExceptionHelper() {
  }

  /**
   * Maps given exception to http response
   *
   * @param throwable exception
   * @return response
   */
  public static Response handleException(Throwable throwable) {
    if (throwable.getClass() == UnprocessableEntityException.class) {
      UnprocessableEntityException exception = ((UnprocessableEntityException) throwable);
      List<Error> errors = exception.getErrors().stream()
        .map(e -> new Error()
          .withCode(e.getCode())
          .withMessage(e.getMessage()))
        .collect(Collectors.toList());
      return Response.status(HttpStatus.SC_UNPROCESSABLE_ENTITY)
        .type(MediaType.APPLICATION_JSON)
        .entity(new Errors().withErrors(errors).withTotalRecords(errors.size()))
        .build();
    }
    LOG.error(throwable.getMessage(), throwable);
    return Response.status(HttpStatus.SC_INTERNAL_SERVER_ERROR)
      .type(MediaType.TEXT_PLAIN)
      .entity(Response.Status.INTERNAL_SERVER_ERROR.getReasonPhrase())
      .build();
  }
}

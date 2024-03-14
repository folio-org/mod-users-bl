package org.folio.rest.util;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.apache.hc.core5.http.HttpStatus;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.exception.TokenNotFoundException;
import org.folio.rest.exception.UnprocessableEntityException;
import org.folio.rest.jaxrs.model.Error;
import org.folio.rest.jaxrs.model.Errors;

public final class ExceptionHelper {

  private static final Logger LOG = LogManager.getLogger(ExceptionHelper.class);

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
    if (throwable.getClass() == NoSuchElementException.class) {
      return Response.status(HttpStatus.SC_BAD_REQUEST)
        .type(MediaType.TEXT_PLAIN)
        .entity(throwable.getMessage())
        .build();
    } else if (throwable.getClass() == TokenNotFoundException.class){
      return Response.status(HttpStatus.SC_NOT_FOUND)
              .type(MediaType.TEXT_PLAIN)
              .entity(throwable.getMessage())
              .build();
    }
    LOG.error(throwable.getMessage(), throwable);
    return Response.status(HttpStatus.SC_INTERNAL_SERVER_ERROR)
      .type(MediaType.TEXT_PLAIN)
      .entity(Response.Status.INTERNAL_SERVER_ERROR.getReasonPhrase())
      .build();
  }
}

package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.serviceproxy.ServiceBinder;
import org.folio.rest.resource.interfaces.InitAPI;
import org.folio.service.password.UserPasswordService;
import org.folio.service.password.UserPasswordServiceImpl;

/**
 * Performs prepossessing operations before the verticle is deployed,
 * e.g. components registration, initializing, binding.
 */
public class InitAPIs implements InitAPI {

  @Override
  public void init(Vertx vertx, Context context, Handler<AsyncResult<Boolean>> handler) {
    new ServiceBinder(vertx)
      .setAddress(UserPasswordServiceImpl.USER_PASS_SERVICE_ADDRESS)
      .register(UserPasswordService.class, UserPasswordService.create(vertx));
    handler.handle(Future.succeededFuture(true));
  }
}

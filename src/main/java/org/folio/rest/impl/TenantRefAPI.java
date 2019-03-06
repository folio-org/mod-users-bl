package org.folio.rest.impl;
import java.util.Map;
import javax.ws.rs.core.Response;
import org.folio.rest.jaxrs.model.TenantAttributes;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.folio.okapi.common.OkapiClient;
import org.folio.okapi.common.XOkapiHeaders;
import org.folio.rest.jaxrs.model.Parameter;

public class TenantRefAPI extends TenantAPI {

  private static final Logger log = LoggerFactory.getLogger(TenantRefAPI.class);

  @Override
  public void postTenant(TenantAttributes ta, Map<String, String> headers,
    Handler<AsyncResult<Response>> hndlr, Context cntxt) {

    log.info("postTenant");
    Vertx vertx = cntxt.owner();
    super.postTenant(ta, headers, res -> {
      if (res.failed()) {
        hndlr.handle(res);
        return;
      }
      if (ta != null) {
        for (Parameter parameter : ta.getParameters()) {
          if ("bootstrapAdmin".equals(parameter.getKey()) && "true".equals(parameter.getValue())) {
            final String okapiUrl = headers.get(XOkapiHeaders.URL);
            OkapiClient cli = new OkapiClient(okapiUrl, vertx, headers);
            String tenant = headers.get(XOkapiHeaders.TENANT);
            checkUserRecord(cli, tenant, headers, res1 -> {
              cli.close();
              if (res1.failed()) {
                hndlr.handle(io.vertx.core.Future.succeededFuture(PostTenantResponse
                  .respond500WithTextPlain(res1.cause().getLocalizedMessage())));
                return;
              }
            });
          }
        }
      }
      hndlr.handle(io.vertx.core.Future.succeededFuture(PostTenantResponse
        .respond201WithApplicationJson("")));
    }, cntxt);
  }

  private void checkUserRecord(OkapiClient cli, String tenant, Map<String, String> headers,
    Handler<AsyncResult<Void>> hndlr) {
    cli.get("/users?query=username%3D%3D" + tenant, res -> {
      if (res.failed()) {
        hndlr.handle(Future.failedFuture(res.cause()));
      } else {
        JsonObject j = new JsonObject(res.result());
        Integer cnt = j.getInteger("totalRecords");
        if (cnt > 0) {
          hndlr.handle(Future.succeededFuture());
          return;
        }
        hndlr.handle(Future.succeededFuture());        
      }
    });
  }
}

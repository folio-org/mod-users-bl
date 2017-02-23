/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.folio.bl.users;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.http.HttpServer;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;


/**
 *
 * @author kurt
 */


public class MainVerticle extends AbstractVerticle {
  
  private final Logger logger = LoggerFactory.getLogger("mod-users-bl");
  public void start(Future<Void> future) {
    Router router = Router.router(vertx);
    HttpServer server = vertx.createHttpServer();
    
    String logLevel = System.getProperty("log.level", null);
    if(logLevel != null) {
      try {
        org.apache.log4j.Logger l4jLogger;
        l4jLogger = org.apache.log4j.Logger.getLogger("mod-users-bl");
        l4jLogger.getParent().setLevel(org.apache.log4j.Level.toLevel(logLevel));
      } catch(Exception e) {
        logger.error("Unable to set log level: " + e.getMessage());
      }
    }
    
    final int port = Integer.parseInt(System.getProperty("port", "8081"));
    
    router.get("/by-id/:id").handler(this::handleRetrieve);
    router.get("/:username").handler(this::handleRetrieve);
  }
  
  private void handleRetrieve(RoutingContext context) {
    
  }
}

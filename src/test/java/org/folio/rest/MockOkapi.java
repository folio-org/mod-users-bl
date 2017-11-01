/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.folio.rest;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 *
 * @author kurt
 */
public class MockOkapi {
  private JsonStore userStore;
  
  public MockOkapi() {
    userStore = new JsonStore();
  }
  
  private MockResponse handleUsers(String verb, String url, String payload, RoutingContext context) {
    int code = 200;
    String response = "";
    if(verb.toUpperCase().equals("GET")) {
      String userId = context.pathParam("userId");
      if(userId != null) {
        JsonObject item = userStore.getItem(userId);
        if(item == null) {
          code = 404;
          response = "Not found";
        } else {
          code = 200;
          response = item.encode();
        }
      } else {
        List<JsonObject> responseList = getCollectionWithContextParams(userStore, context, null);
        JsonObject responseObject = wrapCollection(responseList, "users");
        response = responseObject.encode();
      }      
    } else if(verb.toUpperCase().equals("PUT")) {
      String userId = context.pathParam("userId");
      boolean success = userStore.updateItem(userId, new JsonObject(payload));
      if(success) {
        code = 204;
        response = "";
      } else {
        code = 404;
        response = "Not found";
      }
    } else if(verb.toUpperCase().equals("POST")) {
      JsonObject ob = userStore.addItem(null, new JsonObject(payload));
      if(ob == null) {
        code = 422;
        response = "Unable to add object";
      } else {
        code = 201;
        response = ob.encode();
      }
    } else if(verb.toUpperCase().equals("DELETE")) {
      String userId = context.pathParam("userId");
      boolean success = userStore.deleteItem(userId);
      if(success) {
        code = 204;
        response = "";
      } else {
        code = 404;
        response = "Not found";
      }
    }
    return new MockResponse(code, response);
  }
  
  private JsonObject wrapCollection(List<JsonObject> obList, String collectionName) {
    JsonObject result = new JsonObject();
    JsonArray obArray = new JsonArray();
    for(JsonObject ob : obList) {
      obArray.add(ob);
    }
    result.put(collectionName, obArray);
    result.put("totalRecords", obList.size() );
    return result;
  }
  
  List<JsonObject> getCollectionWithContextParams(JsonStore jsonStore, RoutingContext context, Map<String, String> filterMap) {
    List<JsonObject> result;
    int offset = Integer.parseInt(context.pathParams().getOrDefault("offset", "0"));
    int limit = Integer.parseInt(context.pathParams().getOrDefault("limit", "30"));
    return jsonStore.getCollection(offset, limit, filterMap);
  }
}

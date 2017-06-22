package org.folio.rest.impl;

import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import org.folio.rest.jaxrs.model.CompositeUser;
import org.folio.rest.jaxrs.model.CompositeUserListObject;
import org.folio.rest.jaxrs.model.Credentials;
import org.folio.rest.jaxrs.model.LoginCredentials;
import org.folio.rest.jaxrs.model.PatronGroup;
import org.folio.rest.jaxrs.model.Permissions;
import org.folio.rest.jaxrs.model.User;
import org.folio.rest.jaxrs.resource.BlUsersResource;
import org.folio.rest.tools.client.BuildCQL;
import org.folio.rest.tools.client.HttpModuleClient2;
import org.folio.rest.tools.client.Response;
import org.folio.rest.tools.client.exceptions.PopulateTemplateException;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

/**
 * @author shale
 *
 */
public class BlUsersAPI implements BlUsersResource {

  private static String OKAPI_URL_HEADER = "X-Okapi-URL";
  private static String OKAPI_TENANT_HEADER = "X-Okapi-Tenant";
  private static String OKAPI_TOKEN_HEADER = "X-Okapi-Token";
  private static String OKAPI_PERMISSIONS_HEADER = "X-Okapi-Permissions";
  private final Logger logger = LoggerFactory.getLogger(BlUsersAPI.class);

  @Override
  public void getBlUsersByUsernameByUsername(String username, List<String> include,
      Boolean expandPerms, Map<String, String> okapiHeaders,
      Handler<AsyncResult<javax.ws.rs.core.Response>> asyncResultHandler, Context vertxContext)
      throws Exception {

    run(null, username, expandPerms, include, okapiHeaders, asyncResultHandler, vertxContext);

  }

  Consumer<Response> handlePreviousResponse(boolean requireOneResult, boolean requireOneOrMoreResults, boolean stopChainOnNoResults,
      boolean previousFailure[], Handler<AsyncResult<javax.ws.rs.core.Response>> asyncResultHandler){
    return (response) -> {
      if(!previousFailure[0]){
        handleError(response, requireOneResult, requireOneOrMoreResults, stopChainOnNoResults, previousFailure, asyncResultHandler);
      }
    };
  }

  private void handleError(Response response, boolean requireOneResult, boolean requireOneOrMoreResults, boolean stopOnError,
      boolean previousFailure[], Handler<AsyncResult<javax.ws.rs.core.Response>> asyncResultHandler){

    if(isNull(response)){
      //response is null, meaning the previous call failed.
      //set previousFailure flag to true so that we don't send another error response to the client
      if(!previousFailure[0]){
        asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
          GetBlUsersByIdByIdResponse.withPlainInternalServerError("response is null from one of the services requested")));
      }
      previousFailure[0] = true;
    }
    else {
      //check if the previous request failed
      int statusCode = response.getCode();
      boolean ok = Response.isSuccess(statusCode);
      if(ok){
        //the status code indicates success, check if the amount of results are acceptable from the
        //previous Response
        Integer totalRecords = response.getBody().getInteger("totalRecords");
        if(totalRecords == null){
          totalRecords = response.getBody().getInteger("total_records");
        }
        if(((totalRecords != null && totalRecords < 1) || response.getBody().isEmpty()) && (requireOneResult || requireOneOrMoreResults)) {
          previousFailure[0] = true;
          if(stopOnError){
            //the chained requests will not fire the next request if the response's error object of the previous request is not null
            //so set the response's error object of the previous request to not null so that the calls that are to fire after this
            //are not called
            response.setError(new JsonObject());
          }
          logger.error("No record found for query '" + response.getEndpoint() + "'");
          asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
            GetBlUsersByIdByIdResponse.withPlainNotFound("No record found for query '" + response.getEndpoint() + "'")));
        } else if(totalRecords != null && totalRecords > 1 && requireOneResult) {
          logger.error("'" + response.getEndpoint() + "' returns multiple results");
          previousFailure[0] = true;
          asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
            GetBlUsersByIdByIdResponse.withPlainBadRequest(("'" + response.getEndpoint() + "' returns multiple results"))));
        }
      }
      else if(!ok){
        String message = "";
        if(response.getError() != null){
          statusCode = response.getError().getInteger("statusCode");
          message = response.getError().encodePrettily();
        }
        else{
          Throwable e = response.getException();
          if(e != null){
            message = response.getException().getLocalizedMessage();
            if(e instanceof PopulateTemplateException){
              return;
            }
          }
        }
        if(statusCode == 404){
          logger.error(message);
          previousFailure[0] = true;
          asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
            GetBlUsersByIdByIdResponse.withPlainNotFound(message)));
        }
        else if(statusCode == 400){
          logger.error(message);
          previousFailure[0] = true;
          asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
            GetBlUsersByIdByIdResponse.withPlainBadRequest(message)));
        }
        else if(statusCode == 403){
          logger.error(message);
          previousFailure[0] = true;
          asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
            GetBlUsersByIdByIdResponse.withPlainForbidden(message)));
        }
        else{
          logger.error(message);
          previousFailure[0] = true;
          asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
            GetBlUsersByIdByIdResponse.withPlainInternalServerError(message)));
        }
      }
    }
  }

  @Override
  public void getBlUsersByIdById(String userid, List<String> include, Boolean expandPerms,
      Map<String, String> okapiHeaders, Handler<AsyncResult<javax.ws.rs.core.Response>> asyncResultHandler,
      Context vertxContext) throws Exception {
    run(userid, null, expandPerms, include, okapiHeaders, asyncResultHandler, vertxContext);
  }

  private void run(String userid, String username, Boolean expandPerms, List<String> include,
      Map<String, String> okapiHeaders, Handler<AsyncResult<javax.ws.rs.core.Response>> asyncResultHandler,
      Context vertxContext) throws Exception {

    //works on single user, no joins needed , just aggregate

    boolean []aRequestHasFailed = new boolean[]{false};
    String tenant = okapiHeaders.get(OKAPI_TENANT_HEADER);
    String okapiURL = okapiHeaders.get(OKAPI_URL_HEADER);
    okapiHeaders.remove(OKAPI_URL_HEADER);

    HttpModuleClient2 client = new HttpModuleClient2(okapiURL, tenant);

    CompletableFuture<Response> []userIdResponse = new CompletableFuture[1];
    String userTemplate = "";
    String groupTemplate = "";
    StringBuffer userUrl = new StringBuffer("/users");
    String mode[] = new String[1];
    if(userid != null) {
      userUrl.append("/").append(userid);
      userIdResponse[0] = client.request(userUrl.toString(), okapiHeaders);
      userTemplate = "{username}";
      groupTemplate = "{patronGroup}";
      mode[0] = "id";
    }
    else if(username != null){
      userUrl.append("?query=username=").append(username);
      userIdResponse[0] = client.request(userUrl.toString(), okapiHeaders);
      userTemplate = "{users[0].username}";
      groupTemplate = "{users[0].patronGroup}";
      mode[0] = "username";
    }

    int includeCount = include.size();
    ArrayList<CompletableFuture<Response>> requestedIncludes = new ArrayList<>();
    Map<String, CompletableFuture<Response>> completedLookup = new HashMap<>();

    for (int i = 0; i < includeCount; i++) {

      if(include.get(i).equals("credentials")){
        //call credentials once the /users?query=username={username} completes
        CompletableFuture<Response> credResponse = userIdResponse[0].thenCompose(
              client.chainedRequest("/authn/credentials/"+userTemplate, okapiHeaders, null,
                handlePreviousResponse(true, false, true, aRequestHasFailed, asyncResultHandler)));
        requestedIncludes.add(credResponse);
        completedLookup.put("credentials", credResponse);
      }
      else if(include.get(i).equals("perms")){
        //call perms once the /users?query=username={username} (same as creds) completes
        CompletableFuture<Response> permResponse = userIdResponse[0].thenCompose(
              client.chainedRequest("/perms/users/"+userTemplate, okapiHeaders, null,
                handlePreviousResponse(true, false, true, aRequestHasFailed, asyncResultHandler)));
        requestedIncludes.add(permResponse);
        completedLookup.put("perms", permResponse);
      }
      else if(include.get(i).equals("groups")){
        CompletableFuture<Response> groupResponse = userIdResponse[0].thenCompose(
          client.chainedRequest("/groups/"+groupTemplate, okapiHeaders, null,
            handlePreviousResponse(true, false, true, aRequestHasFailed, asyncResultHandler)));
        requestedIncludes.add(groupResponse);
        completedLookup.put("groups", groupResponse);
      }
    }
    if(expandPerms != null && expandPerms){
      CompletableFuture<Response> expandPermsResponse = userIdResponse[0].thenCompose(
        client.chainedRequest("/perms/users/"+userTemplate+"/permissions?expanded=true&full=true", okapiHeaders, true, null,
          handlePreviousResponse(true, false, true, aRequestHasFailed, asyncResultHandler)));
      requestedIncludes.add(expandPermsResponse);
      completedLookup.put("expanded", expandPermsResponse);
    }
    requestedIncludes.add(userIdResponse[0]);
    CompletableFuture.allOf(requestedIncludes.toArray(new CompletableFuture[requestedIncludes.size()]))
    .thenAccept((response) -> {
      try {
        Response userResponse = userIdResponse[0].get();
        if(requestedIncludes.size() == 1){
          //no includes requested, so users response was not validated, so validate
          boolean requireOneResult;
          boolean requireOneOrMoreResults;
          if(mode[0].equals("id")){
            requireOneResult= true;
            requireOneOrMoreResults = false;
          }else{
            requireOneResult= true;
            requireOneOrMoreResults = true;
          }
          handleError(userResponse, requireOneResult, requireOneOrMoreResults, true, aRequestHasFailed, asyncResultHandler);
          if(aRequestHasFailed[0]){
            return;
          }
        }
        CompositeUser cu = new CompositeUser();
        if(mode[0].equals("id")){
          cu.setUser((User)userResponse.convertToPojo(User.class));
        }
        else if(mode[0].equals("username")){
          if(responseOk(userResponse)){
            cu.setUser((User)Response.convertToPojo(userResponse.getBody().getJsonArray("users").getJsonObject(0), User.class));
          }
        }
        CompletableFuture<Response> cf = completedLookup.get("credentials");
        if(cf != null && cf.get().getBody() != null){
          cu.setCredentials((Credentials)Response.convertToPojo(cf.get().getBody(), Credentials.class));
        }
        cf = completedLookup.get("groups");
        if(cf != null && cf.get().getBody() != null){
          cu.setPatronGroup((PatronGroup)cf.get().convertToPojo(PatronGroup.class) );
        }
        cf = completedLookup.get("perms");
        if(cf != null && cf.get().getBody() != null){
          cu.setPermissions((Permissions)Response.convertToPojo(cf.get().getBody(), Permissions.class));
        }
        cf = completedLookup.get("expanded");
        if(cf != null && cf.get().getBody() != null){
          //data coming in from the service isnt returned as required by the composite user schema
          JsonObject j = new JsonObject();
          j.put("permissions", cf.get().getBody().getJsonArray("permissionNames"));
          cu.setPermissions((Permissions)Response.convertToPojo(j, Permissions.class));
        }
        cf = completedLookup.get("perms");
        if(cf != null && cf.get().getBody() != null){
          Permissions p = cu.getPermissions();
          if(p != null){
            //expanded permissions requested and the array of permissions has been populated
            //add the username
            p.setUsername(cf.get().getBody().getString("username"));
          } else{
            cu.setPermissions((Permissions)Response.convertToPojo(cf.get().getBody(), Permissions.class));
          }
        }
        client.closeClient();
        if(mode[0].equals("id")){
          asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
            GetBlUsersByIdByIdResponse.withJsonOK(cu)));
        }else if(mode[0].equals("username")){
          asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
            GetBlUsersByUsernameByUsernameResponse.withJsonOK(cu)));
        }
      } catch (Exception e) {
        if(!aRequestHasFailed[0]){
          logger.error(e.getMessage(), e);
          if(mode[0].equals("id")){
            asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
              GetBlUsersByIdByIdResponse.withPlainInternalServerError(e.getLocalizedMessage())));
          }else if(mode[0].equals("username")){
            asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
              GetBlUsersByUsernameByUsernameResponse.withPlainInternalServerError(e.getLocalizedMessage())));
          }
        }
      }
    });
  }

  @Override
  public void getBlUsers(String query, int offset, int limit,
      List<String> include, Map<String, String> okapiHeaders,
      Handler<AsyncResult<javax.ws.rs.core.Response>> asyncResultHandler, Context vertxContext)
      throws Exception {

    //works on multiple users, joins needed to aggregate

    boolean []aRequestHasFailed = new boolean[]{false};
    String tenant = okapiHeaders.get(OKAPI_TENANT_HEADER);
    String okapiURL = okapiHeaders.get(OKAPI_URL_HEADER);
    HttpModuleClient2 client = new HttpModuleClient2(okapiURL, tenant);
    okapiHeaders.remove(OKAPI_URL_HEADER);
    CompletableFuture<Response> []userIdResponse = new CompletableFuture[1];

    StringBuffer userUrl = new StringBuffer("/users?");
    if(query != null){
      userUrl.append("query=").append(query).append("&");
    }
    userUrl.append("offset=").append(offset).append("&limit=").append(limit);

    userIdResponse[0] = client.request(userUrl.toString(), okapiHeaders);

    int includeCount = include.size();
    ArrayList<CompletableFuture<Response>> requestedIncludes = new ArrayList<>();
    //CompletableFuture<Response> []requestedIncludes = new CompletableFuture[includeCount+1];
    Map<String, CompletableFuture<Response>> completedLookup = new HashMap<>();

    for (int i = 0; i < includeCount; i++) {

      if(include.get(i).equals("credentials")){
        //call credentials once the /users?query=username={username} completes
        CompletableFuture<Response> credResponse = userIdResponse[0].thenCompose(
              client.chainedRequest("/authn/credentials", okapiHeaders, new BuildCQL(null, "users[*].username", "username"),
                handlePreviousResponse(false, true, true, aRequestHasFailed, asyncResultHandler)));
        requestedIncludes.add(credResponse);
        completedLookup.put("credentials", credResponse);
      }
      else if(include.get(i).equals("perms")){
        //call perms once the /users?query=username={username} (same as creds) completes
        CompletableFuture<Response> permResponse = userIdResponse[0].thenCompose(
              client.chainedRequest("/perms/users", okapiHeaders, new BuildCQL(null, "users[*].username", "username"),
                handlePreviousResponse(false, true, true, aRequestHasFailed, asyncResultHandler)));
        requestedIncludes.add(permResponse);
        completedLookup.put("perms", permResponse);
      }
      else if(include.get(i).equals("groups")){
        CompletableFuture<Response> groupResponse = userIdResponse[0].thenCompose(
          client.chainedRequest("/groups", okapiHeaders, new BuildCQL(null, "users[*].patronGroup", "id"),
            handlePreviousResponse(false, true, true, aRequestHasFailed, asyncResultHandler)));
        requestedIncludes.add(groupResponse);
        completedLookup.put("groups", groupResponse);
      }
    }
    requestedIncludes.add(userIdResponse[0]);
    CompletableFuture.allOf(requestedIncludes.toArray(new CompletableFuture[requestedIncludes.size()]))
    .thenAccept((response) -> {
      try {
        Response userResponse = userIdResponse[0].get();
        if(requestedIncludes.size() == 1){
          //no includes requested, so users response was not validated, so validate
          handleError(userResponse, false, true, true, aRequestHasFailed, asyncResultHandler);
        }
        if(aRequestHasFailed[0]){
          return;
        }
        CompositeUserListObject cu = new CompositeUserListObject();
        Response composite = new Response();
        //map an array of users returned by /users into an array of compositeUser objects - "compositeUser": []
        //name each object in the array "users" -  "compositeUser": [ { "users": { ...
        composite.mapFrom(userResponse, "users[*]", "compositeUser", "users", true);
        if(composite.getBody().isEmpty()){
          if(!aRequestHasFailed[0]){
            asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
              GetBlUsersResponse.withJsonOK(cu)));
          }
          aRequestHasFailed[0] = true;
          return;
        }
        Response groupResponse = null;
        Response credsResponse = null;
        Response permsResponse = null;
        CompletableFuture<Response> cf = completedLookup.get("groups");
        if(cf != null){
          groupResponse = cf.get();
          //check for errors
          handleError(groupResponse, false, true, false, aRequestHasFailed, asyncResultHandler);
          if(!aRequestHasFailed[0]){
            //join into the compositeUser array groups joining on id and patronGroup field values. assume only one group per user
            //hence the usergroup[0] field to push into ../../groups otherwise (if many) leave out the [0] and pass in "usergroups"
            composite.joinOn("compositeUser[*].users.patronGroup", groupResponse, "usergroups[*].id", "../", "../../groups", false);
          }
        }
        cf = completedLookup.get("credentials");
        if(cf != null){
          credsResponse = cf.get();
          //check for errors
          handleError(credsResponse, false, true, false, aRequestHasFailed, asyncResultHandler);
          if(!aRequestHasFailed[0]){
            composite.joinOn("compositeUser[*].users.username", credsResponse, "credentials[*].username", "../", "../../credentials", false);
          }
        }
        cf = completedLookup.get("perms");
        if(cf != null){
          permsResponse = cf.get();
          //check for errors
          handleError(permsResponse, false, true, false, aRequestHasFailed, asyncResultHandler);
          if(!aRequestHasFailed[0]){
            composite.joinOn("compositeUser[*].users.username", permsResponse, "permissionUsers[*].username", "../permissions", "../../permissions.permissions", false);
          }
        }
        client.closeClient();
        @SuppressWarnings("unchecked")
        List<CompositeUser> cuol = (List<CompositeUser>)Response.convertToPojo(composite.getBody().getJsonArray("compositeUser"), CompositeUser.class);
        cu.setCompositeUsers(cuol);
        if(!aRequestHasFailed[0]){
          asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
            GetBlUsersResponse.withJsonOK(cu)));
        }
      } catch (Exception e) {
        if(!aRequestHasFailed[0]){
          asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
            GetBlUsersResponse.withPlainInternalServerError(e.getLocalizedMessage())));
        }
        logger.error(e.getMessage(), e);
      }
    });
  }

  private boolean responseOk(Response r){
    if(r != null && r.getBody() != null){
      return true;
    }
    return false;
  }

  private boolean isNull(Response r){
    if(r != null){
      return false;
    }
    return true;
  }

  private String getUsername(String token) {
    JsonObject payload = parseTokenPayload(token);
    if(payload == null) { return null; }
    String username = payload.getString("sub");
    return username;
  }

  private JsonObject parseTokenPayload(String token) {
    String[] tokenParts = token.split("\\.");
    if(tokenParts.length == 3) {
      String encodedPayload = tokenParts[1];
      byte[] decodedJsonBytes = Base64.getDecoder().decode(encodedPayload);
      String decodedJson = new String(decodedJsonBytes);
      return new JsonObject(decodedJson);
    } else {
      return null;
    }
  }

  @Override
  public void getBlUsersSelf(List<String> include, Boolean expandPerms, Map<String, String> okapiHeaders,
      Handler<AsyncResult<javax.ws.rs.core.Response>> asyncResultHandler, Context vertxContext)
      throws Exception {
    String token = okapiHeaders.get(OKAPI_TOKEN_HEADER);
    String username = getUsername(token);
    run(null, username, expandPerms, include, okapiHeaders, asyncResultHandler, vertxContext);
  }

  @Override
  public void postBlUsersLogin(Boolean expandPerms, List<String> include, LoginCredentials entity,
      Map<String, String> okapiHeaders,
      Handler<AsyncResult<javax.ws.rs.core.Response>> asyncResultHandler, Context vertxContext)
      throws Exception {

    //works on single user, no joins needed , just aggregate

    String tenant = okapiHeaders.get(OKAPI_TENANT_HEADER);
    String okapiURL = okapiHeaders.get(OKAPI_URL_HEADER);

    HttpModuleClient2 client = new HttpModuleClient2(okapiURL, tenant);
    okapiHeaders.remove(OKAPI_URL_HEADER);

    CompletableFuture<Response> loginResponse[] = new CompletableFuture[1];
    CompletableFuture<Response> userResponse[] = new CompletableFuture[1];

    boolean []aRequestHasFailed = new boolean[]{false};

    if(entity == null || entity.getUsername() == null || entity.getPassword() == null) {
      asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
        PostBlUsersLoginResponse.withPlainBadRequest("Improperly formatted request")));
    } else {
      String moduleURL = "/authn/login";
      logger.debug("Requesting login from " + moduleURL);
      //can only be one user with this username - so only one result expected
      String userUrl = "/users?query=username="+entity.getUsername();
      //run login
      loginResponse[0] = client.request(HttpMethod.POST, entity, moduleURL, okapiHeaders);
      //then get user by username, inject okapi headers from the login response into the user request
      //see 'true' flag passed into the chainedRequest
      userResponse[0] = loginResponse[0].thenCompose(client.chainedRequest(userUrl,
        okapiHeaders, true, null, handlePreviousResponse(false, false, true, aRequestHasFailed, asyncResultHandler)));

      //populate composite based on includes
      int includeCount = include.size();
      ArrayList<CompletableFuture<Response>> requestedIncludes = new ArrayList<>();
      Map<String, CompletableFuture<Response>> completedLookup = new HashMap<>();

      for (int i = 0; i < includeCount; i++) {

        if(include.get(i).equals("credentials")){
          //call credentials once the /users?query=username={username} completes
          CompletableFuture<Response> credResponse = userResponse[0].thenCompose(
                client.chainedRequest("/authn/credentials", okapiHeaders, new BuildCQL(null, "users[*].username", "username"),
                  handlePreviousResponse(false, true, true, aRequestHasFailed, asyncResultHandler)));
          requestedIncludes.add(credResponse);
          completedLookup.put("credentials", credResponse);
        }
        else if(include.get(i).equals("perms")){
          //call perms once the /users?query=username={username} (same as creds) completes
          CompletableFuture<Response> permResponse = userResponse[0].thenCompose(
                client.chainedRequest("/perms/users", okapiHeaders, new BuildCQL(null, "users[*].username", "username"),
                  handlePreviousResponse(false, true, true, aRequestHasFailed, asyncResultHandler)));
          requestedIncludes.add(permResponse);
          completedLookup.put("perms", permResponse);
        }
        else if(include.get(i).equals("groups")){
          CompletableFuture<Response> groupResponse = userResponse[0].thenCompose(
            client.chainedRequest("/groups/{users[0].patronGroup}", okapiHeaders, null,
              handlePreviousResponse(false, true, true, aRequestHasFailed, asyncResultHandler)));
          requestedIncludes.add(groupResponse);
          completedLookup.put("groups", groupResponse);
        }
      }

      if(expandPerms != null && expandPerms){
        CompletableFuture<Response> expandPermsResponse = userResponse[0].thenCompose(
          client.chainedRequest("/perms/users/{users[0].username}/permissions?expanded=true&full=true", okapiHeaders, true, null,
            handlePreviousResponse(true, false, true, aRequestHasFailed, asyncResultHandler)));
        requestedIncludes.add(expandPermsResponse);
        completedLookup.put("expanded", expandPermsResponse);
      }
      requestedIncludes.add(userResponse[0]);

      CompletableFuture.allOf(requestedIncludes.toArray(new CompletableFuture[requestedIncludes.size()]))
      .thenAccept((response) -> {
        try {
          if(requestedIncludes.size() == 1){
            //no includes requested, so users response was not validated, so validate
            handleError(userResponse[0].get(), true, false, true, aRequestHasFailed, asyncResultHandler);
          }
          if(aRequestHasFailed[0]){
            return;
          }

          String token = loginResponse[0].get().getHeaders().get(OKAPI_TOKEN_HEADER);

          //all requested endpoints have completed, proces....
          CompositeUser cu = new CompositeUser();
          //user errors handled in chainedRequest, so assume user is ok at this point
          cu.setUser((User)Response.convertToPojo(
            userResponse[0].get().getBody().getJsonArray("users").getJsonObject(0), User.class));

          CompletableFuture<Response> cf = completedLookup.get("groups");
          if(cf != null){
            Response groupResponse = cf.get();
            handleError(groupResponse, false, true, false, aRequestHasFailed, asyncResultHandler);
            if(!aRequestHasFailed[0] && groupResponse.getBody() != null){
              cu.setPatronGroup((PatronGroup)Response.convertToPojo(groupResponse.getBody(), PatronGroup.class));
            }
          }
          cf = completedLookup.get("credentials");
          if(cf != null){
            Response credsResponse = cf.get();
            handleError(credsResponse, false, true, false, aRequestHasFailed, asyncResultHandler);
            if(!aRequestHasFailed[0] && credsResponse.getBody() != null){
              cu.setCredentials((Credentials)Response.convertToPojo(
                credsResponse.getBody().getJsonArray("credentials").getJsonObject(0), Credentials.class));
            }
          }
          cf = completedLookup.get("expanded");
          if(cf != null){
            Response permsResponse = cf.get();
            handleError(permsResponse, false, true, false, aRequestHasFailed, asyncResultHandler);
            if(!aRequestHasFailed[0] && permsResponse.getBody() != null){
              //data coming in from the service isnt returned as required by the composite user schema
              JsonObject j = new JsonObject();
              j.put("permissions", permsResponse.getBody().getJsonArray("permissionNames"));
              cu.setPermissions((Permissions) Response.convertToPojo(j, Permissions.class));
            }
          }
          cf = completedLookup.get("perms");
          if(cf != null && cf.get().getBody() != null){
            Response permsResponse = cf.get();
            handleError(permsResponse, false, true, false, aRequestHasFailed, asyncResultHandler);
            if(!aRequestHasFailed[0]){
              Permissions p = cu.getPermissions();
              if(p != null){
                //expanded permissions requested and the array of permissions has been populated
                //add the username
                p.setUsername(permsResponse.getBody().getJsonArray("permissionUsers").getJsonObject(0).getString("username"));
              } else{
                //data coming in from the service isnt returned as required by the composite user schema
                JsonObject j = permsResponse.getBody().getJsonArray("permissionUsers").getJsonObject(0);
                cu.setPermissions((Permissions) Response.convertToPojo(j, Permissions.class));              }
            }
          }

          client.closeClient();
          if(!aRequestHasFailed[0]){
            asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
              PostBlUsersLoginResponse.withJsonCreated(token, cu)));
          }
        } catch (Exception e) {
          if(!aRequestHasFailed[0]){
            asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
              PostBlUsersLoginResponse.withPlainInternalServerError(e.getLocalizedMessage())));
          }
          logger.error(e.getMessage(), e);
        }
      });
    }
  }

}

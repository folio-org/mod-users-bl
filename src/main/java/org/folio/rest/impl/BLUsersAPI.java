package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.apache.commons.lang3.StringUtils;
import org.folio.rest.client.ConfigurationsClient;
import org.folio.rest.jaxrs.model.CompositeUser;
import org.folio.rest.jaxrs.model.CompositeUserListObject;
import org.folio.rest.jaxrs.model.Credentials;
import org.folio.rest.jaxrs.model.Errors;
import org.folio.rest.jaxrs.model.Identifier;
import org.folio.rest.jaxrs.model.LoginCredentials;
import org.folio.rest.jaxrs.model.PatronGroup;
import org.folio.rest.jaxrs.model.Permissions;
import org.folio.rest.jaxrs.model.ProxiesFor;
import org.folio.rest.jaxrs.model.ServicePoint;
import org.folio.rest.jaxrs.model.ServicePointsUser;

import org.folio.rest.jaxrs.resource.BlUsers;
import org.folio.rest.jaxrs.model.UpdateCredentials;
import org.folio.rest.jaxrs.model.User;
import org.folio.rest.tools.client.BuildCQL;
import org.folio.rest.tools.client.HttpClientFactory;
import org.folio.rest.tools.client.Response;
import org.folio.rest.tools.client.exceptions.PopulateTemplateException;
import org.folio.rest.tools.client.interfaces.HttpClientInterface;
import org.folio.rest.util.OkapiConnectionParams;
import org.folio.services.UserPasswordService;
import org.folio.services.UserPasswordServiceImpl;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author shale
 *
 */
public class BLUsersAPI implements BlUsers {

  private static final String CREDENTIALS_INCLUDE = "credentials";
  private static final String GROUPS_INCLUDE = "groups";
  private static final String PERMISSIONS_INCLUDE = "perms";
  private static final String PROXIESFOR_INCLUDE = "proxiesfor";
  private static final String SERVICEPOINTS_INCLUDE = "servicepoints";

  private static final String EXPANDED_PERMISSIONS_INCLUDE = "expanded_perms";
  private static final String EXPANDED_SERVICEPOINTS_INCLUDE = "expanded_servicepoints";
  private final Logger logger = LoggerFactory.getLogger(BLUsersAPI.class);

  public static final String OKAPI_URL_HEADER = "X-Okapi-URL";
  public static final String OKAPI_TENANT_HEADER = "X-Okapi-Tenant";
  public static final String OKAPI_TOKEN_HEADER = "X-Okapi-Token";
  public static final String OKAPI_USER_ID = "X-Okapi-User-Id";

  public static final String LOCATE_USER_USERNAME = "userName";
  public static final String LOCATE_USER_PHONE_NUMBER = "phoneNumber";
  public static final String LOCATE_USER_EMAIL = "email";

  private static final Pattern HOST_PORT_PATTERN = Pattern.compile("https?://([^:/]+)(?::?(\\d+)?)");

  private static final int DEFAULT_PORT = 9030;

  private UserPasswordService userPasswordService;

  public BLUsersAPI(Vertx vertx, String tenantId) { //NOSONAR
    this.userPasswordService = UserPasswordService
      .createProxy(vertx, UserPasswordServiceImpl.USER_PASS_SERVICE_ADDRESS);
  }

  private List<String> getDefaultIncludes(){
    List<String> defaultIncludes = new ArrayList<>();
    defaultIncludes.add(GROUPS_INCLUDE);
    defaultIncludes.add(PERMISSIONS_INCLUDE);
    defaultIncludes.add(SERVICEPOINTS_INCLUDE);
    return defaultIncludes;
  }

  @Override
  public void getBlUsersByUsernameByUsername(String username, List<String> include,
    boolean expandPerms, Map<String, String> okapiHeaders,
    Handler<AsyncResult<javax.ws.rs.core.Response>> asyncResultHandler,
    Context vertxContext) {
    run(null, username, expandPerms, include, okapiHeaders, asyncResultHandler,
      vertxContext);
  }

  Consumer<Response> handlePreviousResponse(boolean requireOneResult,
      boolean requireOneOrMoreResults, boolean stopChainOnNoResults,
      boolean[] previousFailure, Handler<AsyncResult<javax.ws.rs.core.Response>> asyncResultHandler) {
    return (response) -> {
      if(!previousFailure[0]){
        handleResponse(response, requireOneResult, requireOneOrMoreResults,
            stopChainOnNoResults, previousFailure, asyncResultHandler);
      }
    };
  }

  private void handleResponse(Response response, boolean requireOneResult,
      boolean requireOneOrMoreResults, boolean stopOnError, boolean previousFailure[],
      Handler<AsyncResult<javax.ws.rs.core.Response>> asyncResultHandler) {

    if(previousFailure[0]){
      return;
    }
    if(isNull(response)){
      //response is null, meaning the previous call failed.
      //set previousFailure flag to true so that we don't send another error response to the client
      if(!previousFailure[0]){
        asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
          GetBlUsersByIdByIdResponse.respond500WithTextPlain(
              "response is null from one of the services requested")));
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
        if(((totalRecords != null && totalRecords < 1) || response.getBody().isEmpty())
            && (requireOneResult || requireOneOrMoreResults)) {
          previousFailure[0] = true;
          if(stopOnError){
            //the chained requests will not fire the next request if the response's error object of the previous request is not null
            //so set the response's error object of the previous request to not null so that the calls that are to fire after this
            //are not called
            response.setError(new JsonObject());
          }
          logger.error("No record found for query '" + response.getEndpoint() + "'");
          asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
            GetBlUsersByIdByIdResponse.respond404WithTextPlain("No record found for query '"
                + response.getEndpoint() + "'")));
        } else if(totalRecords != null && totalRecords > 1 && requireOneResult) {
          logger.error("'" + response.getEndpoint() + "' returns multiple results");
          previousFailure[0] = true;
          asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
            GetBlUsersByIdByIdResponse.respond400WithTextPlain(("'" + response.getEndpoint()
                + "' returns multiple results"))));
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
          asyncResultHandler.handle(Future.succeededFuture(
            GetBlUsersByIdByIdResponse.respond404WithTextPlain(message)));
        }
        else if(statusCode == 400){
          logger.error(message);
          previousFailure[0] = true;
          asyncResultHandler.handle(Future.succeededFuture(
            GetBlUsersByIdByIdResponse.respond400WithTextPlain(message)));
        }
        else if(statusCode == 403){
          logger.error(message);
          previousFailure[0] = true;
          asyncResultHandler.handle(Future.succeededFuture(
            GetBlUsersByIdByIdResponse.respond403WithTextPlain(message)));
        }
        else{
          logger.error(message);
          previousFailure[0] = true;
          asyncResultHandler.handle(Future.succeededFuture(
            GetBlUsersByIdByIdResponse.respond500WithTextPlain(message)));
        }
      }
    }
  }

  @Override
  public void getBlUsersByIdById(String userid, List<String> include, boolean expandPerms, Map<String, String> okapiHeaders, 
    Handler<AsyncResult<javax.ws.rs.core.Response>> asyncResultHandler, Context vertxContext) {
    run(userid, null, expandPerms, include, okapiHeaders, asyncResultHandler,
        vertxContext);
  }

  private void run(String userid, String username, Boolean expandPerms,
          List<String> include, Map<String, String> okapiHeaders,
          Handler<AsyncResult<javax.ws.rs.core.Response>> asyncResultHandler,
          Context vertxContext) {

    //works on single user, no joins needed , just aggregate

    if(include == null || include.isEmpty()){
      //by default return perms and groups
      include = getDefaultIncludes();
    }

    boolean[] aRequestHasFailed = new boolean[]{false};
    String tenant = okapiHeaders.get(OKAPI_TENANT_HEADER);
    String okapiURL = okapiHeaders.get(OKAPI_URL_HEADER);
    okapiHeaders.remove(OKAPI_URL_HEADER);

    //HttpModuleClient2 client = new HttpModuleClient2(okapiURL, tenant);
    HttpClientInterface client = HttpClientFactory.getHttpClient(okapiURL, tenant);

    CompletableFuture<Response>[] userIdResponse = new CompletableFuture[1];
    String userTemplate = "";
    String groupTemplate = "";
    StringBuffer userUrl = new StringBuffer("/users");
    String mode[] = new String[1];
    try {
      if (userid != null) {
        userUrl.append("/").append(userid);
        userIdResponse[0] = client.request(userUrl.toString(), okapiHeaders);
        userTemplate = "{id}";
        groupTemplate = "{patronGroup}";
        mode[0] = "id";
      } else if (username != null) {
        userUrl.append("?query=username==").append(username);
        userIdResponse[0] = client.request(userUrl.toString(), okapiHeaders);
        userTemplate = "{users[0].id}";
        groupTemplate = "{users[0].patronGroup}";
        mode[0] = "username";
      }
    } catch (Exception ex) {
      asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
        GetBlUsersByIdByIdResponse.respond500WithTextPlain(ex.getLocalizedMessage())));
      return;
    }
    int includeCount = include.size();
    ArrayList<CompletableFuture<Response>> requestedIncludes = new ArrayList<>();
    Map<String, CompletableFuture<Response>> completedLookup = new HashMap<>();
    logger.info(String.format("Received includes: %s", String.join(",", include)));

    for (int i = 0; i < includeCount; i++) {

      if(include.get(i).equals(CREDENTIALS_INCLUDE)){
        //call credentials once the /users?query=username={username} completes
        CompletableFuture<Response> credResponse = userIdResponse[0].thenCompose(
            client.chainedRequest("/authn/credentials?query=userId=="+userTemplate,
            okapiHeaders, null, handlePreviousResponse(true, false, true,
            aRequestHasFailed, asyncResultHandler)));
        requestedIncludes.add(credResponse);
        completedLookup.put(CREDENTIALS_INCLUDE, credResponse);
      }
      else if(include.get(i).equals(PERMISSIONS_INCLUDE)){
        //call perms once the /users?query=username={username} (same as creds) completes
        CompletableFuture<Response> permResponse = userIdResponse[0].thenCompose(
              client.chainedRequest("/perms/users?query=userId=="+userTemplate,
              okapiHeaders, null, handlePreviousResponse(true, false, true,
              aRequestHasFailed, asyncResultHandler)));
        requestedIncludes.add(permResponse);
        completedLookup.put(PERMISSIONS_INCLUDE, permResponse);
      }
      else if(include.get(i).equals(GROUPS_INCLUDE)){
        CompletableFuture<Response> groupResponse = userIdResponse[0].thenCompose(
          client.chainedRequest("/groups/"+groupTemplate, okapiHeaders, null,
            handlePreviousResponse(true, false, true, aRequestHasFailed, asyncResultHandler)));
        requestedIncludes.add(groupResponse);
        completedLookup.put(GROUPS_INCLUDE, groupResponse);
      }
      else if(include.get(i).equals(PROXIESFOR_INCLUDE)) {
        CompletableFuture<Response> proxiesforResponse = userIdResponse[0].thenCompose(
          client.chainedRequest("/proxiesfor?query=userId==" + userTemplate, okapiHeaders,
            null, handlePreviousResponse(true, false, true, aRequestHasFailed,
              asyncResultHandler)));
        requestedIncludes.add(proxiesforResponse);
        completedLookup.put(PROXIESFOR_INCLUDE, proxiesforResponse);
      }
      else if(include.get(i).equals(SERVICEPOINTS_INCLUDE)) {
        CompletableFuture<Response> servicePointsResponse = userIdResponse[0].thenCompose(
          client.chainedRequest("/service-points-users?query=userId==" + userTemplate,
              okapiHeaders, null, handlePreviousResponse(false, false, false,
              aRequestHasFailed, asyncResultHandler))
        );
        requestedIncludes.add(servicePointsResponse);
        completedLookup.put(SERVICEPOINTS_INCLUDE, servicePointsResponse);
      }
    }
    if(expandPerms != null && expandPerms && completedLookup.containsKey(
        PERMISSIONS_INCLUDE)) {
      logger.info("Getting expanded permissions");
      CompletableFuture<Response> expandPermsResponse = completedLookup.get(
          PERMISSIONS_INCLUDE)
          .thenCompose(
              client.chainedRequest("/perms/users/{permissionUsers[0].id}/permissions?expanded=true&full=true",
              okapiHeaders, true, null, handlePreviousResponse(true, false, true,
              aRequestHasFailed, asyncResultHandler)));
      requestedIncludes.add(expandPermsResponse);
      completedLookup.put(EXPANDED_PERMISSIONS_INCLUDE, expandPermsResponse);
    }
    try {

      if (completedLookup.containsKey(SERVICEPOINTS_INCLUDE)) {
        CompletableFuture<Response> expandSPUResponse = expandServicePoints(
          completedLookup.get(SERVICEPOINTS_INCLUDE), client, aRequestHasFailed,
          okapiHeaders, asyncResultHandler);
        completedLookup.put(EXPANDED_SERVICEPOINTS_INCLUDE, expandSPUResponse);
        requestedIncludes.add(expandSPUResponse);

      }
    } catch (Exception ex) {
      asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
        GetBlUsersByIdByIdResponse.respond500WithTextPlain(ex.getLocalizedMessage())));
      return;
    }
    requestedIncludes.add(userIdResponse[0]);
    CompletableFuture.allOf(requestedIncludes.toArray(
        new CompletableFuture[requestedIncludes.size()]))
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
          handleResponse(userResponse, requireOneResult, requireOneOrMoreResults, true, aRequestHasFailed, asyncResultHandler);
        }
        if(aRequestHasFailed[0]){
          return;
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
        CompletableFuture<Response> cf = completedLookup.get(CREDENTIALS_INCLUDE);
        if(cf != null && cf.get().getBody() != null){
          cu.setCredentials((Credentials)Response.convertToPojo(cf.get().getBody(), Credentials.class));
        }
        cf = completedLookup.get(GROUPS_INCLUDE);
        if(cf != null && cf.get().getBody() != null){
          cu.setPatronGroup((PatronGroup)cf.get().convertToPojo(PatronGroup.class) );
        }
        cf = completedLookup.get(PERMISSIONS_INCLUDE);
        if(cf != null && cf.get().getBody() != null){
          JsonObject permissionsJson = new JsonObject();
          permissionsJson.put("permissions", cf.get().getBody().getJsonArray("permissionUsers").getJsonObject(0).getJsonArray("permissions"));
          cu.setPermissions((Permissions)Response.convertToPojo(permissionsJson, Permissions.class));
        }
        cf = completedLookup.get(EXPANDED_PERMISSIONS_INCLUDE);
        if(cf != null && cf.get().getBody() != null){
          //data coming in from the service isnt returned as required by the composite user schema
          JsonObject j = new JsonObject();
          j.put("permissions", cf.get().getBody().getJsonArray("permissionNames"));
          cu.setPermissions((Permissions)Response.convertToPojo(j, Permissions.class));
        }
        cf = completedLookup.get(PERMISSIONS_INCLUDE);
        if(cf != null && cf.get().getBody() != null){
          Permissions p = cu.getPermissions();
          if(p != null){
            //expanded permissions requested and the array of permissions has been populated
            //add the username
            //p.setId(cf.get().getBody().getString("username"));
          } else{
            cu.setPermissions((Permissions)Response.convertToPojo(cf.get().getBody(), Permissions.class));
          }
        }
        cf = completedLookup.get(PROXIESFOR_INCLUDE);
        if(cf != null && cf.get().getBody() != null) {
          JsonArray array = cf.get().getBody().getJsonArray("proxiesFor");
          List<ProxiesFor> proxyForList = new ArrayList<>();
          for(Object ob : array) {
            ProxiesFor proxyfor = new ProxiesFor();
            proxyfor = (ProxiesFor)Response.convertToPojo((JsonObject)ob, ProxiesFor.class);
            proxyForList.add(proxyfor);
          }
          if(!proxyForList.isEmpty()) {
            cu.setProxiesFor(proxyForList);
          }
        }

        cf = completedLookup.get(SERVICEPOINTS_INCLUDE);
        CompletableFuture<Response> ecf = completedLookup.get(
            EXPANDED_SERVICEPOINTS_INCLUDE);
        if(ecf != null && cf != null && cf.get().getBody() != null) {
          JsonArray array = cf.get().getBody().getJsonArray("servicePointsUsers");
          if(!array.isEmpty()) {
            JsonObject spuJson = array.getJsonObject(0);
            ServicePointsUser spu = (ServicePointsUser)Response.convertToPojo(spuJson,
                ServicePointsUser.class);
            Response resp = null;
            List<ServicePoint> spList = new ArrayList<>();
            try {
              resp = ecf.get();
            } catch(Exception e) {
              logger.error(String.format(
                  "Unable to get expanded service point Response: %s",
                  e.getLocalizedMessage()));
            }
            if(resp != null) {
              JsonObject spCollectionJson = resp.getBody();
              if(spCollectionJson != null) {
                JsonArray spArray = spCollectionJson.getJsonArray("servicepoints");
                if(spArray != null) {
                  for(Object ob: spArray) {
                    JsonObject json = (JsonObject)ob;
                    ServicePoint sp = (ServicePoint)Response.convertToPojo(json,
                        ServicePoint.class);
                    spList.add(sp);
                  }
                  spu.setServicePoints(spList);
                }
              }
            }
            cu.setServicePointsUser(spu);
          }
        }

        client.closeClient();
        if(mode[0].equals("id")){
          asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
            GetBlUsersByIdByIdResponse.respond200WithApplicationJson(cu)));
        }else if(mode[0].equals("username")){
          asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
            GetBlUsersByUsernameByUsernameResponse.respond200WithApplicationJson(cu)));
        }
      } catch (Exception e) {
        if(!aRequestHasFailed[0]){
          logger.error(e.getMessage(), e);
          if(mode[0].equals("id")){
            asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
              GetBlUsersByIdByIdResponse.respond500WithTextPlain(e.getLocalizedMessage())));
          }else if(mode[0].equals("username")){
            asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
              GetBlUsersByUsernameByUsernameResponse.respond500WithTextPlain(e.getLocalizedMessage())));
          }
        }
      }
    });
  }

  @Override
  public void getBlUsers(String query, int offset, int limit,
      List<String> include, Map<String, String> okapiHeaders,
      Handler<AsyncResult<javax.ws.rs.core.Response>> asyncResultHandler, Context vertxContext) {

    //works on multiple users, joins needed to aggregate

    boolean[] aRequestHasFailed = new boolean[]{false};
    String tenant = okapiHeaders.get(OKAPI_TENANT_HEADER);
    String okapiURL = okapiHeaders.get(OKAPI_URL_HEADER);
    //HttpModuleClient2 client = new HttpModuleClient2(okapiURL, tenant);
    HttpClientInterface client = HttpClientFactory.getHttpClient(okapiURL, tenant);

    okapiHeaders.remove(OKAPI_URL_HEADER);
    CompletableFuture<Response> []userIdResponse = new CompletableFuture[1];

    if(include == null || include.isEmpty()){
      //by default return perms and groups
      include = getDefaultIncludes();
    }

    StringBuffer userUrl = new StringBuffer("/users?");
    if(query != null){
      userUrl.append("query=").append(query).append("&");
    }
    userUrl.append("offset=").append(offset).append("&limit=").append(limit);

    try {
      userIdResponse[0] = client.request(userUrl.toString(), okapiHeaders);
    } catch (Exception ex) {
      asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
        GetBlUsersByIdByIdResponse.respond500WithTextPlain(ex.getLocalizedMessage())));
      return;
    }
    int includeCount = include.size();
    ArrayList<CompletableFuture<Response>> requestedIncludes = new ArrayList<>();
    //CompletableFuture<Response> []requestedIncludes = new CompletableFuture[includeCount+1];
    Map<String, CompletableFuture<Response>> completedLookup = new HashMap<>();

    for (int i = 0; i < includeCount; i++) {

      if(include.get(i).equals(CREDENTIALS_INCLUDE)){
        //call credentials once the /users?query=username={username} completes
        CompletableFuture<Response> credResponse = userIdResponse[0].thenCompose(
            client.chainedRequest("/authn/credentials", okapiHeaders,
            new BuildCQL(null, "users[*].id", "userId"),
            handlePreviousResponse(false, true, true, aRequestHasFailed,
            asyncResultHandler)));
        requestedIncludes.add(credResponse);
        completedLookup.put(CREDENTIALS_INCLUDE, credResponse);
      }
      else if(include.get(i).equals(PERMISSIONS_INCLUDE)){
        //call perms once the /users?query=username={username} (same as creds) completes
        CompletableFuture<Response> permResponse = userIdResponse[0].thenCompose(
              client.chainedRequest("/perms/users", okapiHeaders, new BuildCQL(null, "users[*].id", "userId"),
                handlePreviousResponse(false, true, true, aRequestHasFailed, asyncResultHandler)));
        requestedIncludes.add(permResponse);
        completedLookup.put(PERMISSIONS_INCLUDE, permResponse);
      }
      else if(include.get(i).equals(GROUPS_INCLUDE)){
        CompletableFuture<Response> groupResponse = userIdResponse[0].thenCompose(
          client.chainedRequest("/groups", okapiHeaders, new BuildCQL(null, "users[*].patronGroup", "id"),
            handlePreviousResponse(false, true, true, aRequestHasFailed, asyncResultHandler)));
        requestedIncludes.add(groupResponse);
        completedLookup.put(GROUPS_INCLUDE, groupResponse);
      }
      else if(include.get(i).equals(PROXIESFOR_INCLUDE)) {
        CompletableFuture<Response> proxiesforResponse = userIdResponse[0].thenCompose(
          client.chainedRequest("/proxiesfor", okapiHeaders, new BuildCQL(null, "users[*].id", "userId"),
            handlePreviousResponse(false, true, true, aRequestHasFailed, asyncResultHandler)));
        requestedIncludes.add(proxiesforResponse);
        completedLookup.put(PROXIESFOR_INCLUDE, proxiesforResponse);
      }
    }
    requestedIncludes.add(userIdResponse[0]);
    CompletableFuture.allOf(requestedIncludes.toArray(new CompletableFuture[requestedIncludes.size()]))
    .thenAccept((response) -> {
      try {
        Response userResponse = userIdResponse[0].get();
        if(requestedIncludes.size() == 1){
          //no includes requested, so users response was not validated, so validate
          handleResponse(userResponse, false, true, true, aRequestHasFailed, asyncResultHandler);
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
              GetBlUsersResponse.respond200WithApplicationJson(cu)));
          }
          aRequestHasFailed[0] = true;
          return;
        }
        Response groupResponse = null;
        Response credsResponse = null;
        Response permsResponse = null;
        Response proxiesforResponse = null;
        Response servicePointsUserResponse = null;
        CompletableFuture<Response> cf = completedLookup.get(GROUPS_INCLUDE);
        if(cf != null){
          groupResponse = cf.get();
          //check for errors
          handleResponse(groupResponse, false, true, false, aRequestHasFailed, asyncResultHandler);
          if(!aRequestHasFailed[0]){
            //join into the compositeUser array groups joining on id and patronGroup field values. assume only one group per user
            //hence the usergroup[0] field to push into ../../groups otherwise (if many) leave out the [0] and pass in "usergroups"
            composite.joinOn("compositeUser[*].users.patronGroup", groupResponse, "usergroups[*].id", "../", "../../groups", false);
          }
        }
        cf = completedLookup.get(CREDENTIALS_INCLUDE);
        if(cf != null){
          credsResponse = cf.get();
          //check for errors
          handleResponse(credsResponse, false, true, false, aRequestHasFailed, asyncResultHandler);
          if(!aRequestHasFailed[0]){
            composite.joinOn("compositeUser[*].users.id", credsResponse, "credentials[*].userId", "../", "../../credentials", false);
          }
        }
        cf = completedLookup.get(PERMISSIONS_INCLUDE);
        if(cf != null){
          permsResponse = cf.get();
          //check for errors
          handleResponse(permsResponse, false, true, false, aRequestHasFailed, asyncResultHandler);
          if(!aRequestHasFailed[0]){
            composite.joinOn("compositeUser[*].users.id", permsResponse, "permissionUsers[*].userId", "../permissions", "../../permissions.permissions", false);
          }
        }
        cf = completedLookup.get(PROXIESFOR_INCLUDE);
        if(cf != null) {
          proxiesforResponse = cf.get();
          handleResponse(proxiesforResponse, false, true, false, aRequestHasFailed, asyncResultHandler);
          if(!aRequestHasFailed[0]) {
            composite.joinOn("compositeUser[*].users.id", proxiesforResponse, "proxiesFor[*].userId", "../", "../../proxiesFor", false);
          }
        }
        client.closeClient();
        @SuppressWarnings("unchecked")
        List<CompositeUser> cuol = (List<CompositeUser>)Response.convertToPojo(composite.getBody().getJsonArray("compositeUser"), CompositeUser.class);
        cu.setCompositeUsers(cuol);
        if(!aRequestHasFailed[0]){
          asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
            GetBlUsersResponse.respond200WithApplicationJson(cu)));
        }
      } catch (Exception e) {
        if(!aRequestHasFailed[0]){
          asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
            GetBlUsersResponse.respond500WithTextPlain(e.getLocalizedMessage())));
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
  public void getBlUsersSelf(List<String> include, boolean expandPerms,
          Map<String, String> okapiHeaders,
          Handler<AsyncResult<javax.ws.rs.core.Response>> asyncResultHandler,
          Context vertxContext) {
    String token = okapiHeaders.get(OKAPI_TOKEN_HEADER);
    String username = getUsername(token);
    run(null, username, expandPerms, include, okapiHeaders, asyncResultHandler, vertxContext);
  }

  @Override
  public void postBlUsersLogin(boolean expandPerms, List<String> include,
      LoginCredentials entity, Map<String, String> okapiHeaders,
      Handler<AsyncResult<javax.ws.rs.core.Response>> asyncResultHandler,
      Context vertxContext) {

    //works on single user, no joins needed , just aggregate

    String tenant = okapiHeaders.get(OKAPI_TENANT_HEADER);
    String okapiURL = okapiHeaders.get(OKAPI_URL_HEADER);

    //HttpModuleClient2 client = new HttpModuleClient2(okapiURL, tenant);
    HttpClientInterface client = HttpClientFactory.getHttpClient(okapiURL, tenant);

    okapiHeaders.remove(OKAPI_URL_HEADER);

    CompletableFuture<Response> loginResponse[] = new CompletableFuture[1];
    CompletableFuture<Response> userResponse[] = new CompletableFuture[1];

    boolean []aRequestHasFailed = new boolean[]{false};

    if(include == null || include.isEmpty()){
      //by default return perms and groups
      include = getDefaultIncludes();
    }

    if (entity == null || entity.getUsername() == null || entity.getPassword() == null) {
      asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
        PostBlUsersLoginResponse.respond400WithTextPlain("Improperly formatted request")));
    } else {
      String moduleURL = "/authn/login";
      logger.debug("Requesting login from " + moduleURL);
      //can only be one user with this username - so only one result expected
      String userUrl = "/users?query=username=" + entity.getUsername();
      //run login
      try {
        loginResponse[0] = client.request(HttpMethod.POST, entity, moduleURL,
          okapiHeaders);
      } catch (Exception ex) {
        asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
          PostBlUsersLoginResponse.respond500WithTextPlain(ex.getLocalizedMessage())));
        return;
      }
      //then get user by username, inject okapi headers from the login response into the user request
      //see 'true' flag passed into the chainedRequest
      userResponse[0] = loginResponse[0].thenCompose(client.chainedRequest(
        userUrl, okapiHeaders, false, null, handlePreviousResponse(false,
          false, true, aRequestHasFailed, asyncResultHandler)));

      //populate composite based on includes
      int includeCount = include.size();
      ArrayList<CompletableFuture<Response>> requestedIncludes
          = new ArrayList<>();
      Map<String, CompletableFuture<Response>> completedLookup
          = new HashMap<>();

      for (int i = 0; i < includeCount; i++) {

        if(include.get(i).equals(CREDENTIALS_INCLUDE)){
          //call credentials once the /users?query=username={username} completes
          CompletableFuture<Response> credResponse = userResponse[0]
              .thenCompose(client.chainedRequest("/authn/credentials",
              okapiHeaders, new BuildCQL(null, "users[*].id", "userId"),
              handlePreviousResponse(false, true, true, aRequestHasFailed,
              asyncResultHandler)));
          requestedIncludes.add(credResponse);
          completedLookup.put(CREDENTIALS_INCLUDE, credResponse);
        }
        else if(include.get(i).equals(PERMISSIONS_INCLUDE)){
          //call perms once the /users?query=username={username} (same as creds) completes
          CompletableFuture<Response> permResponse = userResponse[0].thenCompose(
                client.chainedRequest("/perms/users", okapiHeaders, new BuildCQL(null, "users[*].id", "userId"),
                  handlePreviousResponse(false, true, true, aRequestHasFailed, asyncResultHandler)));
          requestedIncludes.add(permResponse);
          completedLookup.put(PERMISSIONS_INCLUDE, permResponse);
        }
        else if(include.get(i).equals(GROUPS_INCLUDE)){
          CompletableFuture<Response> groupResponse = userResponse[0].thenCompose(
            client.chainedRequest("/groups/{users[0].patronGroup}", okapiHeaders, null,
              handlePreviousResponse(false, true, true, aRequestHasFailed, asyncResultHandler)));
          requestedIncludes.add(groupResponse);
          completedLookup.put(GROUPS_INCLUDE, groupResponse);
        }
        else if(include.get(i).equals(SERVICEPOINTS_INCLUDE)) {
          CompletableFuture<Response> servicePointsResponse = userResponse[0].thenCompose(
            client.chainedRequest("/service-points-users?query=userId=={users[0].id}",
                okapiHeaders, null, handlePreviousResponse(false, false, false,
                aRequestHasFailed, asyncResultHandler))
          );
          requestedIncludes.add(servicePointsResponse);
          completedLookup.put(SERVICEPOINTS_INCLUDE, servicePointsResponse);
          try {
            CompletableFuture<Response> expandSPUResponse = expandServicePoints(
              servicePointsResponse, client, aRequestHasFailed, okapiHeaders,
              asyncResultHandler);
            completedLookup.put(EXPANDED_SERVICEPOINTS_INCLUDE, expandSPUResponse);
            requestedIncludes.add(expandSPUResponse);
          } catch (Exception ex) {
            asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
              PostBlUsersLoginResponse.respond500WithTextPlain(ex.getLocalizedMessage())));
            return;
          }
        }
      }

      if (expandPerms){
        CompletableFuture<Response> permUserResponse = userResponse[0].thenCompose(
          client.chainedRequest("/perms/users", okapiHeaders, new BuildCQL(null, "users[*].id", "userId"),
            handlePreviousResponse(false, true, true, aRequestHasFailed, asyncResultHandler))
        );
        CompletableFuture<Response> expandPermsResponse = permUserResponse.thenCompose(
          client.chainedRequest("/perms/users/{permissionUsers[0].id}/permissions?expanded=true&full=true", okapiHeaders, true, null,
            handlePreviousResponse(true, false, true, aRequestHasFailed, asyncResultHandler)));
        requestedIncludes.add(expandPermsResponse);
        completedLookup.put(EXPANDED_PERMISSIONS_INCLUDE, expandPermsResponse);
      }
      requestedIncludes.add(userResponse[0]);

      CompletableFuture.allOf(requestedIncludes.toArray(new CompletableFuture[requestedIncludes.size()]))
      .thenAccept((response) -> {
        try {
          if(requestedIncludes.size() == 1){
            //no includes requested, so users response was not validated, so validate
            handleResponse(userResponse[0].get(), true, false, true, aRequestHasFailed, asyncResultHandler);
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

          CompletableFuture<Response> cf = completedLookup.get(GROUPS_INCLUDE);
          if(cf != null){
            Response groupResponse = cf.get();
            handleResponse(groupResponse, false, true, false, aRequestHasFailed, asyncResultHandler);
            if(!aRequestHasFailed[0] && groupResponse.getBody() != null){
              cu.setPatronGroup((PatronGroup)Response.convertToPojo(groupResponse.getBody(), PatronGroup.class));
            }
          }
          cf = completedLookup.get(CREDENTIALS_INCLUDE);
          if(cf != null){
            Response credsResponse = cf.get();
            handleResponse(credsResponse, false, true, false, aRequestHasFailed, asyncResultHandler);
            if(!aRequestHasFailed[0] && credsResponse.getBody() != null){
              cu.setCredentials((Credentials)Response.convertToPojo(
                credsResponse.getBody().getJsonArray("credentials").getJsonObject(0), Credentials.class));
            }
          }
          cf = completedLookup.get(EXPANDED_PERMISSIONS_INCLUDE);
          if(cf != null){
            Response permsResponse = cf.get();
            handleResponse(permsResponse, false, true, false, aRequestHasFailed, asyncResultHandler);
            if(!aRequestHasFailed[0] && permsResponse.getBody() != null){
              //data coming in from the service isnt returned as required by the composite user schema
              JsonObject j = new JsonObject();
              j.put("permissions", permsResponse.getBody().getJsonArray("permissionNames"));
              cu.setPermissions((Permissions) Response.convertToPojo(j, Permissions.class));
            }
          }
          cf = completedLookup.get(PERMISSIONS_INCLUDE);
          if(cf != null && cf.get().getBody() != null){
            Response permsResponse = cf.get();
            handleResponse(permsResponse, false, true, false, aRequestHasFailed, asyncResultHandler);
            if(!aRequestHasFailed[0]){
              Permissions p = cu.getPermissions();
              if(p != null){
                //expanded permissions requested and the array of permissions has been populated
                //add the username
                p.setUserId(permsResponse.getBody().getJsonArray("permissionUsers").getJsonObject(0).getString("id"));
              } else{
                //data coming in from the service isnt returned as required by the composite user schema
                JsonObject j = permsResponse.getBody().getJsonArray("permissionUsers").getJsonObject(0);
                cu.setPermissions((Permissions) Response.convertToPojo(j, Permissions.class));
              }
            }
          }

          //TODO: Refactor so less copy/paste

          cf = completedLookup.get(SERVICEPOINTS_INCLUDE);
          CompletableFuture<Response> ecf = completedLookup.get(
              EXPANDED_SERVICEPOINTS_INCLUDE);
          if(ecf != null && cf != null && cf.get().getBody() != null) {
            JsonArray array = cf.get().getBody().getJsonArray("servicePointsUsers");
            if(!array.isEmpty()) {
              JsonObject spuJson = array.getJsonObject(0);
              ServicePointsUser spu = (ServicePointsUser)Response.convertToPojo(spuJson,
                  ServicePointsUser.class);
              List<ServicePoint> spList = new ArrayList<>();
              Response resp = ecf.get();
              if(resp != null) {
                JsonObject spCollectionJson = resp.getBody();
                if(spCollectionJson != null) {
                  JsonArray spArray = spCollectionJson.getJsonArray("servicepoints");
                  if(spArray != null) {
                    for(Object ob: spArray) {
                      JsonObject json = (JsonObject)ob;
                      ServicePoint sp = (ServicePoint)Response.convertToPojo(json,
                          ServicePoint.class);
                      spList.add(sp);
                    }
                    spu.setServicePoints(spList);
                  }
                }
              }
              cu.setServicePointsUser(spu);
            }
          }

          client.closeClient();
          if(!aRequestHasFailed[0]){
            asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
              PostBlUsersLoginResponse.respond201WithApplicationJson(cu,
                PostBlUsersLoginResponse.headersFor201().withXOkapiToken(token))));
          }
        } catch (Exception e) {
          if(!aRequestHasFailed[0]){
            asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
              PostBlUsersLoginResponse.respond500WithTextPlain(e.getLocalizedMessage())));
          }
          logger.error(e.getMessage(), e);
        }
      });
    }
  }

  private CompletableFuture<Response> expandServicePoints(
      CompletableFuture<Response> spuResponseFuture, HttpClientInterface client,
      boolean[] aRequestHasFailed, Map<String, String> okapiHeaders,
      Handler<AsyncResult<javax.ws.rs.core.Response>> asyncResultHandler)
      throws InterruptedException, ExecutionException {
    if(spuResponseFuture == null) {
      return CompletableFuture.completedFuture(null);
    }
    return spuResponseFuture.thenCompose( response -> {
      List<String> servicePointIdQueryList = new ArrayList<>();

      JsonObject servicePointsUserListObjectJson = response.getBody();
      if(servicePointsUserListObjectJson == null ) {
        return CompletableFuture.completedFuture(null);
      }
      JsonObject servicePointsUserJson = null;
      try {
        servicePointsUserJson = servicePointsUserListObjectJson
            .getJsonArray("servicePointsUsers").getJsonObject(0);
      } catch(Exception e) {
        //meh
      }
      if(servicePointsUserJson == null) {
        return CompletableFuture.completedFuture(null);
      }
      if(servicePointsUserJson.containsKey("defaultServicePointId")) {
        String defaultSPId = servicePointsUserJson.getString("defaultServicePointId");
        if(defaultSPId != null) {
          servicePointIdQueryList.add(String.format("id==\"%s\"", defaultSPId));
        }
      }
      if(servicePointsUserJson.containsKey("servicePointsIds")) {
        JsonArray SPIdArray = servicePointsUserJson.getJsonArray("servicePointsIds");
        if(SPIdArray != null) {
          for(Object ob : SPIdArray) {
            servicePointIdQueryList.add(String.format("id==\"%s\"", (String)ob));
          }
        }
      }
      if(servicePointIdQueryList.isEmpty()) {
        return CompletableFuture.completedFuture(null);
      }
      String idQuery = null;
      try {
        idQuery = URLEncoder.encode(String.join(" or ", servicePointIdQueryList), "UTF-8");
      } catch (UnsupportedEncodingException ex) {
        java.util.logging.Logger.getLogger(BLUsersAPI.class.getName()).log(Level.SEVERE, null, ex);
      }
      CompletableFuture<Response> expandSPUResponse = spuResponseFuture
          .thenCompose(client.chainedRequest("/service-points?query="+ idQuery,
          okapiHeaders, true, null, handlePreviousResponse(false, false, false,
          aRequestHasFailed, asyncResultHandler)));

      return expandSPUResponse;
    });
  }

  /**
   *
   * @param locateUserFields - a list of fields to be used for search
   * @param value - a value to search
   * @return
   */
  private String buildQuery(List<String> locateUserFields, String value) {
    return locateUserFields.stream()
      .map(field -> new StringBuilder(field).append("==\"").append(value).append("\"").toString())
      .collect(Collectors.joining(" or "));
  }

  /**
   * Maps aliases to configuration parameters
   * @param fieldAliasList - a list of aliases
   * @return a list of user fields to use for search
   */
  private io.vertx.core.Future<List<String>> getLocateUserFields(List<String> fieldAliasList, java.util.Map<String, String> okapiHeaders) {
    //TODO:

    String okapiURL = okapiHeaders.get(OKAPI_URL_HEADER);
    String tenant = okapiHeaders.get(OKAPI_TENANT_HEADER);
    String token = okapiHeaders.get(OKAPI_TOKEN_HEADER);

    Matcher matcher = HOST_PORT_PATTERN.matcher(okapiURL);
    if (!matcher.find()) {
      return io.vertx.core.Future.failedFuture("Could not parse okapiURL: " + okapiURL);
    }
    io.vertx.core.Future<List<String>> future = io.vertx.core.Future.future();

    String host = matcher.group(1);
    String port = matcher.group(2);

    ConfigurationsClient configurationsClient = new ConfigurationsClient(host, StringUtils.isNotBlank(port) ? Integer.valueOf(port) : DEFAULT_PORT, tenant, token);
    StringBuilder query = new StringBuilder("module==USERSBL AND (")
      .append(fieldAliasList.stream()
                            .map(f -> new StringBuilder("code==\"").append(f).append("\"").toString())
                            .collect(Collectors.joining(" or ")))
      .append(")");

    try {
      configurationsClient.getEntries(query.toString(), 0, 3, null, null, response ->
        response.bodyHandler(body -> {
          if (response.statusCode() != 200) {
            future.fail("Expected status code 200, got '" + response.statusCode() +
              "' :" + body.toString());
            return;
          }
          JsonObject entries = body.toJsonObject();

          future.complete(
            entries.getJsonArray("configs").stream()
              .map(o -> ((JsonObject) o).getString("value"))
              .flatMap(s -> Stream.of(s.split("[^\\w\\.]+")))
              .collect(Collectors.toList()));
        })
      );
    } catch (UnsupportedEncodingException e) {
      future.fail(e);
    }
    return future;
  }

  /**
   *
   * @param userToNotify
   * @return
   */
  private io.vertx.core.Future<Void> sendResetPasswordNotification(User userToNotify) {
    //TODO: should be implemented once notification functionality is completed.
    return io.vertx.core.Future.succeededFuture();
  }

  /**
   *
   * @param fieldAliasList list of aliases to use [LOCATE_USER_USERNAME LOCATE_USER_PHONE_NUMBER LOCATE_USER_EMAIL]
   * @param entity - an identity with a value
   * @param okapiHeaders
   * @return
   */
  private io.vertx.core.Future<Void> doPostBlUsersForgotten(List<String> fieldAliasList, Identifier entity,
                                                            java.util.Map<String, String> okapiHeaders) {
    io.vertx.core.Future<Void> asyncResult = io.vertx.core.Future.future();
    getLocateUserFields(fieldAliasList, okapiHeaders).setHandler(locateUserFieldsAR -> {
      if (!locateUserFieldsAR.succeeded()) {
        asyncResult.fail(locateUserFieldsAR.cause());
        return;
      }
      String query = buildQuery(locateUserFieldsAR.result(), entity.getId());

      String tenant = okapiHeaders.get(OKAPI_TENANT_HEADER);
      String okapiURL = okapiHeaders.get(OKAPI_URL_HEADER);

      HttpClientInterface client = HttpClientFactory.getHttpClient(okapiURL, tenant);

      okapiHeaders.remove(OKAPI_URL_HEADER);

      try {
        String userUrl = new StringBuilder("/users?").append("query=").append(URLEncoder.encode(query, "UTF-8")).append("&").append("offset=0&limit=2").toString();

        client.request(userUrl, okapiHeaders).thenAccept(userResponse -> {
          String noUserFoundMessage = "User is not found: ";

          if(!responseOk(userResponse)) {
            asyncResult.fail(new NoSuchElementException(noUserFoundMessage + entity.getId()));
            return;
          }

          JsonArray users = userResponse.getBody().getJsonArray("users");
          int arraySize = users.size();
          if (arraySize == 0 || arraySize > 1) {
            asyncResult.fail(new NoSuchElementException(noUserFoundMessage + entity.getId()));
            return;
          }
          try {
            User user = (User) Response.convertToPojo(users.getJsonObject(0), User.class);
            sendResetPasswordNotification(user).setHandler(asyncResult);
          } catch (Exception e) {
            asyncResult.fail(e);
          }
        });

      } catch (Exception e) {
        asyncResult.fail(e);
      }
    });

    return asyncResult;
  }

  /*
   * See MODLOGIN-44-45
   *
   * These methods (postBlUsersForgottenPassword & postBlUsersForgottenUsername) rely on the configuration properties from mod-configuration
   * see BLUsersAPITest.insertData for example
   * { "module" : "USERSBL", "configName" : "fogottenData", "code" : "userName", "description" : "if true userName will be used fot forgot password search", "default" : false, "enabled" : true, "value" : "username" }
   * { "module" : "USERSBL", "configName" : "fogottenData", "code" : "phoneNumber", "description" : "if true personal.phone & personal.mobilePhone will be used for forgot password and forgot user name search", "default" : false, "enabled" : true, "value" : "personal.phone, personal.mobilePhone" }
   * { "module" : "USERSBL", "configName" : "fogottenData", "code" : "email", "description" : "if true personal.email will be used for forgot password and forgot user name search", "default" : false, "enabled" : true, "value" : "personal.email" }
   */
  @Override
  public void postBlUsersForgottenPassword(Identifier entity, java.util.Map<String, String>okapiHeaders, io.vertx.core.Handler<io.vertx.core.AsyncResult<javax.ws.rs.core.Response>>asyncResultHandler, Context vertxContext) {
    doPostBlUsersForgotten(Arrays.asList(LOCATE_USER_USERNAME, LOCATE_USER_PHONE_NUMBER, LOCATE_USER_EMAIL), entity, okapiHeaders)
      .setHandler(ar ->
        asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
          ar.succeeded() ? PostBlUsersForgottenPasswordResponse.respond204() :
            PostBlUsersForgottenPasswordResponse.respond400WithTextPlain(ar.cause().getLocalizedMessage())))
    );
  }

  @Override
  public void postBlUsersForgottenUsername(Identifier entity, java.util.Map<String, String>okapiHeaders, io.vertx.core.Handler<io.vertx.core.AsyncResult<javax.ws.rs.core.Response>>asyncResultHandler, Context vertxContext) {
    doPostBlUsersForgotten(Arrays.asList(LOCATE_USER_PHONE_NUMBER, LOCATE_USER_EMAIL), entity, okapiHeaders)
      .setHandler(ar ->
        asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
          ar.succeeded() ? PostBlUsersForgottenUsernameResponse.respond204() :
            PostBlUsersForgottenUsernameResponse.respond400WithTextPlain(ar.cause().getLocalizedMessage())))
      );

  }

  @Override
  public void postBlUsersSettingsMyprofilePassword(UpdateCredentials entity, Map<String, String> okapiHeaders,
                                                   Handler<AsyncResult<javax.ws.rs.core.Response>> asyncResultHandler,
                                                   Context vertxContext) {
    try {
      vertxContext.runOnContext(c -> {
        OkapiConnectionParams connectionParams = new OkapiConnectionParams(
          okapiHeaders.get(OKAPI_URL_HEADER),
          okapiHeaders.get(OKAPI_TENANT_HEADER),
          okapiHeaders.get(OKAPI_TOKEN_HEADER),
          entity.getUserId());
        userPasswordService.validateNewPassword(JsonObject.mapFrom(entity), JsonObject.mapFrom(connectionParams), h -> {
          if (h.failed()) {
            logger.error("Error during validate new user's password", h.cause());
            Future.succeededFuture(PostBlUsersSettingsMyprofilePasswordResponse
              .respond500WithTextPlain("Internal server error during validate new user's password"));
          } else {
            Errors errors = h.result().mapTo(Errors.class);
            if (errors.getTotalRecords() == 0) {
              userPasswordService.updateUserCredential(JsonObject.mapFrom(entity), JsonObject.mapFrom(connectionParams), r -> {
                if (r.failed()) {
                  asyncResultHandler.handle(Future.succeededFuture(PostBlUsersSettingsMyprofilePasswordResponse
                    .respond500WithTextPlain(r.cause().getMessage())));
                } else {
                  if (r.result().equals(401)) {
                    asyncResultHandler.handle(Future.succeededFuture(PostBlUsersSettingsMyprofilePasswordResponse
                      .respond401WithTextPlain("Invalid credentials")));
                  } else {
                    asyncResultHandler.handle(Future.succeededFuture(PostBlUsersSettingsMyprofilePasswordResponse
                      .respond204WithTextPlain("User's password was successfully updated")));
                  }
                }
              });
            } else {
              asyncResultHandler.handle(Future.succeededFuture(PostBlUsersSettingsMyprofilePasswordResponse
                .respond400WithApplicationJson(errors)));
            }
          }
        });
      });
    } catch (Exception e) {
      logger.error(e.getMessage(), e);
      asyncResultHandler.handle(Future.succeededFuture(PostBlUsersSettingsMyprofilePasswordResponse
        .respond500WithTextPlain("Internal server error during change user's password")));
    }
  }
}

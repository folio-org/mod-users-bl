package org.folio.service.consortia;

import io.vertx.core.Future;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpStatus;
import org.folio.okapi.common.XOkapiHeaders;
import org.folio.rest.exception.UnprocessableEntityException;
import org.folio.rest.exception.UnprocessableEntityMessage;
import org.folio.rest.jaxrs.model.User;
import org.folio.rest.tools.client.Response;
import org.folio.rest.util.OkapiConnectionParams;
import org.folio.rest.util.RestUtil;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

import static org.folio.rest.impl.BLUsersAPI.FORGOTTEN_PASSWORD_FOUND_INACTIVE;
import static org.folio.rest.impl.BLUsersAPI.LOCATE_USER_USERNAME;
import static org.folio.rest.impl.BLUsersAPI.LOCATE_USER_PHONE_NUMBER;
import static org.folio.rest.impl.BLUsersAPI.LOCATE_USER_EMAIL;
import static org.folio.rest.impl.BLUsersAPI.LOCATE_USER_MOBILE_PHONE_NUMBER;

public class CrossTenantUserServiceImpl implements CrossTenantUserService {
  private static final String USER_TENANT_URL_WITH_OR_OPERATION = "/user-tenants?queryOp=or&";
  private static final String USERS_URL = "/users/";
  private static final List<String> LOCATE_CONSORTIA_USER_FIELDS = List.of(LOCATE_USER_USERNAME,
    LOCATE_USER_PHONE_NUMBER, LOCATE_USER_EMAIL, LOCATE_USER_MOBILE_PHONE_NUMBER);

  private final HttpClient httpClient;

  public CrossTenantUserServiceImpl(HttpClient httpClient) {
    this.httpClient = httpClient;
  }

  @Override
  public Future<User> locateCrossTenantUser(String entity, Map<String, String> okapiHeaders, String errorKey) {
    OkapiConnectionParams okapiConnectionParams = new OkapiConnectionParams(okapiHeaders);
    String query = buildQuery(entity);
    String requestUrl = okapiConnectionParams.getOkapiUrl() + USER_TENANT_URL_WITH_OR_OPERATION + query;

    return RestUtil.doRequest(httpClient, requestUrl, HttpMethod.GET,
      okapiConnectionParams.buildHeaders(), StringUtils.EMPTY)
      .compose(resp -> {
        JsonObject userTenantJson = resp.getJson();
        int totalRecords = userTenantJson.getInteger("totalRecords");
        if (totalRecords == 0) {
          return Future.succeededFuture();
        }
        if (totalRecords > 1) {
          String message = String.format("Multiple users associated with '%s'", entity);
          UnprocessableEntityMessage entityMessage = new UnprocessableEntityMessage(errorKey, message);
          return Future.failedFuture(new UnprocessableEntityException(Collections.singletonList(entityMessage)));
        }

        JsonObject userTenantObject = userTenantJson.getJsonArray("userTenants").getJsonObject(0);
        String userId = userTenantObject.getString("userId");
        String userTenantId = userTenantObject.getString("tenantId");

        okapiHeaders.put(XOkapiHeaders.TENANT, userTenantId);
        OkapiConnectionParams okapiConnection = new OkapiConnectionParams(okapiHeaders);
        String userRequestUrl = okapiConnection.getOkapiUrl() + USERS_URL + userId;
        return RestUtil.doRequest(httpClient, userRequestUrl,HttpMethod.GET,
          okapiConnectionParams.buildHeaders(), StringUtils.EMPTY)
          .compose(userResponse -> {
            String noUserFoundMessage = "User is not by id: ";
            if (userResponse.getCode() != HttpStatus.SC_OK) {
              return Future.failedFuture(new NoSuchElementException(noUserFoundMessage + userId));
            }
            try {
              User user = (User) Response.convertToPojo(userResponse.getJson(), User.class);
              if (user != null && !user.getActive()) {
                String message = String.format("Users with id '%s' is not active", userId);
                UnprocessableEntityMessage entityMessage = new UnprocessableEntityMessage(FORGOTTEN_PASSWORD_FOUND_INACTIVE, message);
                return Future.failedFuture(new UnprocessableEntityException(Collections.singletonList(entityMessage)));
              }
              return Future.succeededFuture(user);
            } catch (Exception e) {
              return Future.failedFuture(e);
            }
          });
      });
  }

  private String buildQuery(String value) {
    return LOCATE_CONSORTIA_USER_FIELDS.stream()
      .map(field -> field + "=" + value)
      .collect(Collectors.joining("&"));
  }
}

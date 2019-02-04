package org.folio.rest.jaxrs.model;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;

import java.io.IOException;

import org.folio.rest.tools.utils.ObjectMapperTool;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.vertx.core.json.JsonObject;

public class ServicePointTest {

  @Rule
  public ExpectedException expectedExceptions = ExpectedException.none();

  @Test
  public void canDeserializeFromJson() throws IOException {
    final ObjectMapper mapper = ObjectMapperTool.getMapper();

    final JsonObject servicePointJson = new JsonObject();

    servicePointJson.put("name", "Collection Point A");
    servicePointJson.put("code", "cpA");
    servicePointJson.put("discoveryDisplayName", "Coll Point A");

    final ServicePoint servicePoint = mapper.readValue(servicePointJson.encode(),
      ServicePoint.class);

    assertThat(servicePoint.getName(), is("Collection Point A"));
    assertThat(servicePoint.getCode(), is("cpA"));
    assertThat(servicePoint.getDiscoveryDisplayName(), is("Coll Point A"));
  }

  @Test
  public void canDeserializeFromJsonWithAdditionalProperties() throws IOException {
    final ObjectMapper mapper = ObjectMapperTool.getMapper();

    final JsonObject servicePointJson = new JsonObject();

    servicePointJson.put("name", "Collection Point A");
    servicePointJson.put("code", "cpA");
    servicePointJson.put("discoveryDisplayName", "Coll Point A");
    servicePointJson.put("foo", "bar");

    final JsonObject expiryPeriod = new JsonObject();
    expiryPeriod.put("duration", "1");
    expiryPeriod.put("intervalId", "Weeks");

    servicePointJson.put("holdShelfExpiryPeriod", expiryPeriod);

    final ServicePoint servicePoint = mapper.readValue(servicePointJson.encode(),
      ServicePoint.class);

    assertThat(servicePoint.getName(), is("Collection Point A"));
    assertThat(servicePoint.getCode(), is("cpA"));
    assertThat(servicePoint.getDiscoveryDisplayName(), is("Coll Point A"));
  }

  @Test
  public void canDeserializeFromJsonWithoutRequiredProperties() throws IOException {
    final ObjectMapper mapper = ObjectMapperTool.getMapper();

    final JsonObject servicePointJson = new JsonObject();

    servicePointJson.put("name", "Collection Point A");

    final ServicePoint servicePoint = mapper.readValue(servicePointJson.encode(),
      ServicePoint.class);

    assertThat(servicePoint.getName(), is("Collection Point A"));
    assertThat(servicePoint.getCode(), is(nullValue()));
    assertThat(servicePoint.getDiscoveryDisplayName(), is(nullValue()));
  }
}

package org.folio.rest.jaxrs.model;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import java.io.IOException;

import org.folio.rest.tools.utils.ObjectMapperTool;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;

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

    final ServicePoint servicePoint = mapper.readValue(servicePointJson.encode(),
      ServicePoint.class);

    assertThat(servicePoint.getName(), is("Collection Point A"));
    assertThat(servicePoint.getCode(), is("cpA"));
  }

  @Test
  public void cannotDeserializeFromJsonWithUnexpectedProperties() throws IOException {
    final ObjectMapper mapper = ObjectMapperTool.getMapper();

    final JsonObject servicePointJson = new JsonObject();

    servicePointJson.put("name", "Collection Point A");
    servicePointJson.put("code", "cpA");
    servicePointJson.put("foo", "bar");

    expectedExceptions.expect(UnrecognizedPropertyException.class);

    mapper.readValue(servicePointJson.encode(), ServicePoint.class);
  }
}

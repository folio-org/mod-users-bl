package org.folio.rest.util;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

import io.vertx.core.Vertx;
import org.folio.rest.testing.UtilityClassTester;
import org.junit.Test;

public class HttpClientUtilTest {

  @Test
  public void utilityClass() {
    UtilityClassTester.assertUtilityClass(HttpClientUtil.class);
  }

  @Test
  public void getInstance() {
    var vertx = Vertx.vertx();
    var httpClient1 = HttpClientUtil.getInstance(vertx);
    var httpClient2 = HttpClientUtil.getInstance(vertx);
    var httpClient3 = HttpClientUtil.getInstance(Vertx.vertx());
    assertThat(httpClient1, is(httpClient2));
    assertThat(httpClient1, is(not(httpClient3)));
  }

}

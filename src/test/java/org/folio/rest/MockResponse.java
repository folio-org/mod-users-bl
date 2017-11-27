package org.folio.rest;

/**
 *
 * @author kurt
 */
public class MockResponse {
  private int code;
  private String content;

  public MockResponse(int code, String content) {
    this.code = code;
    this.content = content;
  }

  public int getCode() {
    return code;
  }

  public void setCode(int code) {
    this.code = code;
  }

  public String getContent() {
    return content;
  }

  public void setContent(String content) {
    this.content = content;
  }

}

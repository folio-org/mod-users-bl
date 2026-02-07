package org.folio.rest.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.folio.okapi.testing.UtilityClassTester;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("EnvUtils Tests")
class EnvUtilsTest {

  private static final String TEST_PROPERTY = "test.env.utils.property";
  private static final String TEST_ENV = "TEST_ENV_UTILS_ENV";

  @BeforeEach
  void setUp() {
    // Clear any test properties before each test
    System.clearProperty(TEST_PROPERTY);
  }

  @AfterEach
  void tearDown() {
    // Clean up after tests
    System.clearProperty(TEST_PROPERTY);
  }

  @Test
  @DisplayName("Should be a proper utility class")
  void utilityClass() {
    UtilityClassTester.assertUtilityClass(EnvUtils.class);
  }

  @Nested
  @DisplayName("getEnvOrDefault with Integer conversion")
  class IntegerConversion {

    @Test
    @DisplayName("Should return system property value when set")
    void shouldReturnSystemPropertyValue() {
      System.setProperty(TEST_PROPERTY, "42");

      int result = EnvUtils.getEnvOrDefault(TEST_PROPERTY, TEST_ENV, 10, Integer::parseInt);

      assertEquals(42, result);
    }

    @Test
    @DisplayName("Should return default value when property is not set")
    void shouldReturnDefaultWhenPropertyNotSet() {
      int result = EnvUtils.getEnvOrDefault(TEST_PROPERTY, TEST_ENV, 100, Integer::parseInt);

      assertEquals(100, result);
    }

    @Test
    @DisplayName("Should return default value when property is blank")
    void shouldReturnDefaultWhenPropertyIsBlank() {
      System.setProperty(TEST_PROPERTY, "   ");

      int result = EnvUtils.getEnvOrDefault(TEST_PROPERTY, TEST_ENV, 50, Integer::parseInt);

      assertEquals(50, result);
    }

    @Test
    @DisplayName("Should return default value when property is empty")
    void shouldReturnDefaultWhenPropertyIsEmpty() {
      System.setProperty(TEST_PROPERTY, "");

      int result = EnvUtils.getEnvOrDefault(TEST_PROPERTY, TEST_ENV, 75, Integer::parseInt);

      assertEquals(75, result);
    }

    @Test
    @DisplayName("Should return default value when conversion fails")
    void shouldReturnDefaultWhenConversionFails() {
      System.setProperty(TEST_PROPERTY, "not-a-number");

      int result = EnvUtils.getEnvOrDefault(TEST_PROPERTY, TEST_ENV, 99, Integer::parseInt);

      assertEquals(99, result);
    }
  }

  @Nested
  @DisplayName("getEnvOrDefault with String conversion")
  class StringConversion {

    @Test
    @DisplayName("Should return property value as-is with identity function")
    void shouldReturnPropertyValueAsIs() {
      System.setProperty(TEST_PROPERTY, "hello-world");

      String result = EnvUtils.getEnvOrDefault(TEST_PROPERTY, TEST_ENV, "default", s -> s);

      assertEquals("hello-world", result);
    }

    @Test
    @DisplayName("Should return transformed value")
    void shouldReturnTransformedValue() {
      System.setProperty(TEST_PROPERTY, "hello");

      String result = EnvUtils.getEnvOrDefault(TEST_PROPERTY, TEST_ENV, "default", String::toUpperCase);

      assertEquals("HELLO", result);
    }

    @Test
    @DisplayName("Should return default when property not set")
    void shouldReturnDefaultWhenNotSet() {
      String result = EnvUtils.getEnvOrDefault(TEST_PROPERTY, TEST_ENV, "fallback", s -> s);

      assertEquals("fallback", result);
    }
  }

  @Nested
  @DisplayName("getEnvOrDefault with Boolean conversion")
  class BooleanConversion {

    @Test
    @DisplayName("Should parse true value")
    void shouldParseTrueValue() {
      System.setProperty(TEST_PROPERTY, "true");

      boolean result = EnvUtils.getEnvOrDefault(TEST_PROPERTY, TEST_ENV, false, Boolean::parseBoolean);

      assertTrue(result);
    }

    @Test
    @DisplayName("Should parse false value")
    void shouldParseFalseValue() {
      System.setProperty(TEST_PROPERTY, "false");

      boolean result = EnvUtils.getEnvOrDefault(TEST_PROPERTY, TEST_ENV, true, Boolean::parseBoolean);

      assertFalse(result);
    }

    @Test
    @DisplayName("Should return default for invalid boolean")
    void shouldReturnDefaultForInvalidBoolean() {
      // Note: Boolean.parseBoolean doesn't throw, it returns false for invalid values
      // So this tests the conversion behavior
      System.setProperty(TEST_PROPERTY, "invalid");

      boolean result = EnvUtils.getEnvOrDefault(TEST_PROPERTY, TEST_ENV, true, Boolean::parseBoolean);

      // Boolean.parseBoolean("invalid") returns false, not an exception
      assertFalse(result);
    }
  }

  @Nested
  @DisplayName("getEnvOrDefault with Long conversion")
  class LongConversion {

    @Test
    @DisplayName("Should parse long value")
    void shouldParseLongValue() {
      System.setProperty(TEST_PROPERTY, "9999999999");

      long result = EnvUtils.getEnvOrDefault(TEST_PROPERTY, TEST_ENV, 0L, Long::parseLong);

      assertEquals(9999999999L, result);
    }

    @Test
    @DisplayName("Should return default for overflow value")
    void shouldReturnDefaultForOverflow() {
      System.setProperty(TEST_PROPERTY, "99999999999999999999999");

      long result = EnvUtils.getEnvOrDefault(TEST_PROPERTY, TEST_ENV, -1L, Long::parseLong);

      assertEquals(-1L, result);
    }
  }

  @Nested
  @DisplayName("getEnvOrDefault with null default value")
  class NullDefaultValue {

    @Test
    @DisplayName("Should return null default when property not set")
    void shouldReturnNullDefaultWhenNotSet() {
      String result = EnvUtils.getEnvOrDefault(TEST_PROPERTY, TEST_ENV, null, s -> s);

      assertNull(result);
    }

    @Test
    @DisplayName("Should return null default when conversion fails")
    void shouldReturnNullDefaultWhenConversionFails() {
      System.setProperty(TEST_PROPERTY, "not-a-number");

      Integer result = EnvUtils.getEnvOrDefault(TEST_PROPERTY, TEST_ENV, null, Integer::parseInt);

      assertNull(result);
    }
  }

  @Nested
  @DisplayName("getEnvOrDefault converter exception handling")
  class ConverterExceptionHandling {

    @Test
    @DisplayName("Should handle NullPointerException from converter")
    void shouldHandleNullPointerException() {
      System.setProperty(TEST_PROPERTY, "value");

      String result = EnvUtils.getEnvOrDefault(TEST_PROPERTY, TEST_ENV, "default", s -> {
        throw new NullPointerException("Simulated NPE");
      });

      assertEquals("default", result);
    }

    @Test
    @DisplayName("Should handle IllegalArgumentException from converter")
    void shouldHandleIllegalArgumentException() {
      System.setProperty(TEST_PROPERTY, "INVALID");

      // Simulating enum parsing that throws IllegalArgumentException
      String result = EnvUtils.getEnvOrDefault(TEST_PROPERTY, TEST_ENV, "DEFAULT", s -> {
        if ("INVALID".equals(s)) {
          throw new IllegalArgumentException("No enum constant");
        }
        return s;
      });

      assertEquals("DEFAULT", result);
    }

    @Test
    @DisplayName("Should handle RuntimeException from converter")
    void shouldHandleRuntimeException() {
      System.setProperty(TEST_PROPERTY, "trigger");

      int result = EnvUtils.getEnvOrDefault(TEST_PROPERTY, TEST_ENV, 999, s -> {
        throw new RuntimeException("Unexpected error");
      });

      assertEquals(999, result);
    }
  }
}


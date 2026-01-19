package org.folio.service;

import java.util.Optional;

public enum PasswordResetSetting {
  FOLIO_HOST("resetPasswordHost"),
  RESET_PASSWORD_UI_PATH("resetPasswordPath"),
  FORGOT_PASSWORD_UI_PATH("forgotPasswordPath");

  private final String key;

  public String getKey() {
    return key;
  }

  PasswordResetSetting(String key) {
    this.key = key;
  }

  public static Optional<PasswordResetSetting> fromName(String name) {
    for (PasswordResetSetting setting : PasswordResetSetting.values()) {
      if (setting.name().equalsIgnoreCase(name)) {
        return Optional.of(setting);
      }
    }
    return Optional.empty();
  }

  public static PasswordResetSetting fromValue(String value) {
    for (PasswordResetSetting setting : PasswordResetSetting.values()) {
      if (setting.getKey().equalsIgnoreCase(value)) {
        return setting;
      }
    }
    throw new IllegalArgumentException("No enum constant with value " + value);
  }
}

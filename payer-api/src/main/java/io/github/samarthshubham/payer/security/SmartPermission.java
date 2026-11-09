package io.github.samarthshubham.payer.security;

public enum SmartPermission {
  CREATE('c'),
  READ('r'),
  UPDATE('u'),
  DELETE('d'),
  SEARCH('s');

  private final char code;

  SmartPermission(char code) {
    this.code = code;
  }

  public char code() {
    return code;
  }
}

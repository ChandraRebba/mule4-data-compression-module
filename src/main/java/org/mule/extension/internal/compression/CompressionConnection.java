package org.mule.extension.internal.compression;

public final class CompressionConnection {

  private final String id;

  public CompressionConnection(String id) {
    this.id = id;
  }

  public String getId() {
    return id;
  }

  public void invalidate() {

  }
}

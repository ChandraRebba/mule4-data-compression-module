package org.mule.extension.internal.compression;

public enum CompressionAlgorithm {
    ZSTD("Zstandard"),
    LZ4("LZ4"),
    SNAPPY("Snappy"),
    BROTLI("Brotli");

    private final String displayName;

    CompressionAlgorithm(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}

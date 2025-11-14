package org.mule.extension.internal.compression;

import static org.mule.runtime.extension.api.annotation.param.MediaType.ANY;
import com.github.luben.zstd.Zstd;
import net.jpountz.lz4.LZ4Compressor;
import net.jpountz.lz4.LZ4Factory;
import net.jpountz.lz4.LZ4FastDecompressor;
import org.xerial.snappy.Snappy;
import com.aayushatharva.brotli4j.Brotli4jLoader;
import com.aayushatharva.brotli4j.encoder.Encoder;
import com.aayushatharva.brotli4j.decoder.Decoder;
import org.mule.runtime.extension.api.annotation.param.Content;
import org.mule.runtime.extension.api.annotation.param.Optional;
import org.mule.runtime.extension.api.annotation.param.MediaType;
import org.mule.runtime.extension.api.annotation.param.display.DisplayName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.InputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import org.apache.commons.io.IOUtils;

public class CompressionOperations {

    private static final Logger LOGGER = LoggerFactory.getLogger(CompressionOperations.class);
    private static final LZ4Factory lz4Factory = LZ4Factory.fastestInstance();

    static {
        try {
            Brotli4jLoader.ensureAvailability();
        } catch (Exception e) {
            LOGGER.warn("Brotli native library not available, Brotli compression may not work", e);
        }
    }

    @DisplayName("Compress")
    @MediaType(value = ANY, strict = false)
    public byte[] compress(@Content InputStream data,
                          @Optional(defaultValue = "ZSTD") CompressionAlgorithm algorithm,
                          @Optional(defaultValue = "3") int level) {
                            long startTime = System.currentTimeMillis();
        try {
            byte[] inputData = IOUtils.toByteArray(data);
            LOGGER.debug("Starting {} compression with level [{}]. Input size: {} bytes.",
                        algorithm.getDisplayName(), level, inputData.length);
            long compressionStartTime = System.currentTimeMillis();
            byte[] compressedData;
            switch (algorithm) {
                case ZSTD:
                    compressedData = compressZstd(inputData, level);
                    break;
                case LZ4:
                    compressedData = compressLz4(inputData);
                    break;
                case SNAPPY:
                    compressedData = compressSnappy(inputData);
                    break;
                case BROTLI:
                    compressedData = compressBrotli(inputData, level);
                    break;
                default:
                    throw new IllegalArgumentException("Unsupported compression algorithm: " + algorithm);
            }
            long compressionTime = System.currentTimeMillis() - compressionStartTime;

            double compressionRatio = (double) compressedData.length / inputData.length * 100;
            String ratioStr = String.format("%.2f", compressionRatio);
            long totalTime = System.currentTimeMillis() - startTime;

            if (compressedData.length > inputData.length) {
                LOGGER.warn("{} compression increased size from {} to {} bytes ({}%). Data may already be compressed or not suitable for compression. Time: {}ms (compression: {}ms)",
                            algorithm.getDisplayName(), inputData.length, compressedData.length, ratioStr, totalTime, compressionTime);
            } else {
                LOGGER.info("{} compression: {} bytes -> {} bytes ({}% of original). Time: {}ms (compression: {}ms)",
                           algorithm.getDisplayName(), inputData.length, compressedData.length, ratioStr, totalTime, compressionTime);
            }


            return compressedData;
        } catch (IOException e) {
            throw new RuntimeException("Failed to read input data", e);
        }
    }

    @DisplayName("Decompress")
    @MediaType(value = ANY, strict = false)
    public byte[] decompress(@Content InputStream data,
                            @Optional(defaultValue = "ZSTD") CompressionAlgorithm algorithm) {
                            long startTime = System.currentTimeMillis();
        try {
            byte[] inputData = IOUtils.toByteArray(data);
            LOGGER.debug("Starting {} decompression. Input size: {} bytes.",
                        algorithm.getDisplayName(), inputData.length);
            long decompressionStartTime = System.currentTimeMillis();
            byte[] decompressedData;
            switch (algorithm) {
                case ZSTD:
                    decompressedData = decompressZstd(inputData);
                    break;
                case LZ4:
                    decompressedData = decompressLz4(inputData);
                    break;
                case SNAPPY:
                    decompressedData = decompressSnappy(inputData);
                    break;
                case BROTLI:
                    decompressedData = decompressBrotli(inputData);
                    break;
                default:
                    throw new IllegalArgumentException("Unsupported compression algorithm: " + algorithm);
            }
            long decompressionTime = System.currentTimeMillis() - decompressionStartTime;
            long totalTime = System.currentTimeMillis() - startTime;

            LOGGER.info("{} decompression: {} bytes -> {} bytes. Time: {}ms (decompression: {}ms)",
                       algorithm.getDisplayName(), inputData.length, decompressedData.length, totalTime, decompressionTime);


            return decompressedData;
        } catch (IOException e) {
            throw new RuntimeException("Failed to read input data", e);
        }
    }

    private byte[] compressZstd(byte[] data, int level) {
        return Zstd.compress(data, level);
    }

    private byte[] decompressZstd(byte[] data) {
        long originalSize = Zstd.decompressedSize(data);
        if (originalSize > Integer.MAX_VALUE) {
            String errorMessage = "Decompressed content is too large for a byte array (> 2GB)";
            LOGGER.error(errorMessage);
            throw new IllegalArgumentException(errorMessage);
        }
        return Zstd.decompress(data, (int) originalSize);
    }

    private byte[] compressLz4(byte[] data) {
        LZ4Compressor compressor = lz4Factory.fastCompressor();
        int maxCompressedLength = compressor.maxCompressedLength(data.length);
        byte[] compressed = new byte[maxCompressedLength + 4];

        ByteBuffer buffer = ByteBuffer.wrap(compressed);
        buffer.putInt(data.length);

        int compressedLength = compressor.compress(data, 0, data.length, compressed, 4, maxCompressedLength);
        byte[] result = new byte[compressedLength + 4];
        System.arraycopy(compressed, 0, result, 0, compressedLength + 4);
        return result;
    }

    private byte[] decompressLz4(byte[] data) {
        LZ4FastDecompressor decompressor = lz4Factory.fastDecompressor();

        ByteBuffer buffer = ByteBuffer.wrap(data);
        int storedSize = buffer.getInt();

        byte[] decompressed = new byte[storedSize];
        decompressor.decompress(data, 4, decompressed, 0, storedSize);
        return decompressed;
    }

    private byte[] compressSnappy(byte[] data) {
        try {
            return Snappy.compress(data);
        } catch (Exception e) {
            LOGGER.error("Snappy compression failed", e);
            throw new RuntimeException("Snappy compression failed", e);
        }
    }

    private byte[] decompressSnappy(byte[] data) {
        try {
            return Snappy.uncompress(data);
        } catch (Exception e) {
            LOGGER.error("Snappy decompression failed", e);
            throw new RuntimeException("Snappy decompression failed", e);
        }
    }

    private byte[] compressBrotli(byte[] data, int quality) {
        try {
            int brotliQuality = Math.min(11, Math.max(0, quality));
            Encoder.Parameters params = new Encoder.Parameters().setQuality(brotliQuality);
            return Encoder.compress(data, params);
        } catch (Exception e) {
            LOGGER.error("Brotli compression failed", e);
            throw new RuntimeException("Brotli compression failed", e);
        }
    }

    private byte[] decompressBrotli(byte[] data) {
        try {
            return Decoder.decompress(data).getDecompressedData();
        } catch (Exception e) {
            LOGGER.error("Brotli decompression failed", e);
            throw new RuntimeException("Brotli decompression failed", e);
        }
    }
}

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
import org.mule.runtime.extension.api.annotation.param.Optional;
import org.mule.runtime.extension.api.annotation.param.MediaType;
import org.mule.runtime.extension.api.annotation.param.display.DisplayName;
import org.mule.runtime.extension.api.annotation.param.display.Summary;
import org.mule.runtime.extension.api.annotation.param.display.Example;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Paths;

public class FileCompressionOperations {

    private static final Logger LOGGER = LoggerFactory.getLogger(FileCompressionOperations.class);
    private static final LZ4Factory lz4Factory = LZ4Factory.fastestInstance();

    static {
        try {
            Brotli4jLoader.ensureAvailability();
        } catch (Exception e) {
            LOGGER.warn("Brotli native library not available, Brotli compression may not work", e);
        }
    }

    @DisplayName("Compress File")
    @MediaType(value = ANY, strict = false)
    public String compressFile(@DisplayName("Input File Path")
                              @Summary("Full path to the file to compress (e.g., C:\\data\\file.txt or /home/user/file.txt)")
                              @Example("C:\\data\\file.txt")
                              String inputFilePath,
                              @Optional
                              @DisplayName("Output File Path")
                              @Summary("Full path for the compressed output file (optional, auto-generated if not provided)")
                              @Example("C:\\data\\file.txt.zstd")
                              String outputFilePath,
                              @Optional(defaultValue = "ZSTD")
                              @DisplayName("Algorithm")
                              @Summary("Compression algorithm to use")
                              CompressionAlgorithm algorithm,
                              @Optional(defaultValue = "3")
                              @DisplayName("Compression Level")
                              @Summary("Compression level (1-22)")
                              int level) {
        LOGGER.debug("Starting file compression: {} with algorithm {}", inputFilePath, algorithm.getDisplayName());
        long startTime = System.currentTimeMillis();
        try {
            String cleanPath = inputFilePath;
            if (cleanPath != null) {
                cleanPath = cleanPath.trim();
                if ((cleanPath.startsWith("\"") && cleanPath.endsWith("\"")) ||
                    (cleanPath.startsWith("'") && cleanPath.endsWith("'"))) {
                    cleanPath = cleanPath.substring(1, cleanPath.length() - 1);
                }
            }

            String normalizedPath = cleanPath.replace("/", File.separator);
            File inputFile = new File(normalizedPath);

            LOGGER.debug("Normalized path: {}, Absolute path: {}, Exists: {}",
                        normalizedPath, inputFile.getAbsolutePath(), inputFile.exists());

            if (!inputFile.exists()) {
                throw new FileNotFoundException("Input file not found: " + normalizedPath +
                                              " (absolute: " + inputFile.getAbsolutePath() + ")");
            }
            long readStartTime = System.currentTimeMillis();

            byte[] inputData = Files.readAllBytes(inputFile.toPath());
            long readTime = System.currentTimeMillis() - readStartTime;
            long compressionStartTime = System.currentTimeMillis();
            byte[] compressedData = compressData(inputData, algorithm, level);
            long compressionTime = System.currentTimeMillis() - compressionStartTime;
            String outputPath = outputFilePath;
            if (outputPath == null || outputPath.isEmpty()) {
                outputPath = normalizedPath + "." + algorithm.name().toLowerCase();
            } else {
                outputPath = outputPath.trim();
                if ((outputPath.startsWith("\"") && outputPath.endsWith("\"")) ||
                    (outputPath.startsWith("'") && outputPath.endsWith("'"))) {
                    outputPath = outputPath.substring(1, outputPath.length() - 1);
                }
                outputPath = outputPath.replace("/", File.separator);
            }
            long writeStartTime = System.currentTimeMillis();
            Files.write(Paths.get(outputPath), compressedData);
            long writeTime = System.currentTimeMillis() - writeStartTime;

            double compressionRatioPercent;
            double compressionRatio;
            if (inputData.length == 0) {
                compressionRatioPercent = compressedData.length == 0 ? 0.0 : 100.0;
                compressionRatio = 0.0;
            } else {
                compressionRatioPercent = ((double) compressedData.length / inputData.length) * 100.0;
                compressionRatio = (double) inputData.length / compressedData.length;
            }
            long totalTime = System.currentTimeMillis() - startTime;

            if (compressedData.length > inputData.length) {
                LOGGER.warn("File compression with {} increased size from {} to {} bytes ({}% of original). " +
                        "File may already be compressed or not suitable for compression. Output: {}. " +
                        "Time: {}ms (read: {}ms, compression: {}ms, write: {}ms)",
                        algorithm.getDisplayName(),
                        inputData.length,
                        compressedData.length,
                        String.format("%.2f", compressionRatioPercent),
                        outputPath,
                        totalTime, readTime, compressionTime, writeTime);
            } else {
                LOGGER.info("File compressed successfully: {} -> {} ({}% of original). " +
                        "Time: {}ms (read: {}ms, compression: {}ms, write: {}ms)",
                        inputFilePath,
                        outputPath,
                        String.format("%.2f", compressionRatioPercent),
                        totalTime, readTime, compressionTime, writeTime);
            }

            return outputPath;
        } catch (IOException e) {
            LOGGER.error("File compression failed", e);
            throw new RuntimeException("File compression failed: " + e.getMessage(), e);
        }
    }

    @DisplayName("Decompress File")
    @MediaType(value = ANY, strict = false)
    public String decompressFile(@DisplayName("Input File Path")
                                @Summary("Full path to the compressed file to decompress")
                                @Example("C:\\data\\file.txt.zstd")
                                String inputFilePath,
                                @Optional
                                @DisplayName("Output File Path")
                                @Summary("Full path for the decompressed output file (optional, auto-generated if not provided)")
                                @Example("C:\\data\\file.txt")
                                String outputFilePath,
                                @Optional(defaultValue = "ZSTD")
                                @DisplayName("Algorithm")
                                @Summary("Compression algorithm used")
                                CompressionAlgorithm algorithm) {
        LOGGER.debug("Starting file decompression: {} with algorithm {}", inputFilePath, algorithm.getDisplayName());
        long startTime = System.currentTimeMillis();

        try {
            String cleanPath = inputFilePath;
            if (cleanPath != null) {
                cleanPath = cleanPath.trim();
                if ((cleanPath.startsWith("\"") && cleanPath.endsWith("\"")) ||
                    (cleanPath.startsWith("'") && cleanPath.endsWith("'"))) {
                    cleanPath = cleanPath.substring(1, cleanPath.length() - 1);
                }
            }

            String normalizedPath = cleanPath.replace("/", File.separator);
            File inputFile = new File(normalizedPath);

            LOGGER.debug("Normalized path: {}, Absolute path: {}, Exists: {}",
                        normalizedPath, inputFile.getAbsolutePath(), inputFile.exists());

            if (!inputFile.exists()) {
                throw new FileNotFoundException("Input file not found: " + normalizedPath +
                                              " (absolute: " + inputFile.getAbsolutePath() + ")");
            }
            long readStartTime = System.currentTimeMillis();
            byte[] compressedData = Files.readAllBytes(inputFile.toPath());
            long readTime = System.currentTimeMillis() - readStartTime;
            long decompressionStartTime = System.currentTimeMillis();
            byte[] decompressedData = decompressData(compressedData, algorithm);
            long decompressionTime = System.currentTimeMillis() - decompressionStartTime;

            String outputPath = outputFilePath;
            if (outputPath == null || outputPath.isEmpty()) {
                outputPath = normalizedPath.replaceAll("\\." + algorithm.name().toLowerCase() + "$", ".decompressed");
            } else {
                outputPath = outputPath.trim();
                if ((outputPath.startsWith("\"") && outputPath.endsWith("\"")) ||
                    (outputPath.startsWith("'") && outputPath.endsWith("'"))) {
                    outputPath = outputPath.substring(1, outputPath.length() - 1);
                }
                outputPath = outputPath.replace("/", File.separator);
            }
            long writeStartTime = System.currentTimeMillis();
            Files.write(Paths.get(outputPath), decompressedData);
            long writeTime = System.currentTimeMillis() - writeStartTime;
            long totalTime = System.currentTimeMillis() - startTime;



            double decompressionRatio = compressedData.length > 0 ?
                (double) decompressedData.length / compressedData.length : 0.0;

            LOGGER.info("File decompressed successfully: {} -> {}. Time: {}ms (read: {}ms, decompression: {}ms, write: {}ms)",
                    inputFilePath,
                    outputPath,
                    totalTime, readTime, decompressionTime, writeTime);

            return outputPath;
        } catch (IOException e) {
            LOGGER.error("File decompression failed", e);
            throw new RuntimeException("File decompression failed: " + e.getMessage(), e);
        }
    }

    @DisplayName("Compress Directory")
    @MediaType(value = ANY, strict = false)
    public String compressDirectory(@DisplayName("Directory Path")
                                   @Summary("Full path to the directory to compress")
                                   @Example("C:\\data\\myFolder")
                                   String directoryPath,
                                   @Optional
                                   @DisplayName("Output File Path")
                                   @Summary("Full path for the output archive file (optional, auto-generated if not provided)")
                                   @Example("C:\\data\\myFolder.zstd")
                                   String outputFilePath,
                                   @Optional(defaultValue = "ZSTD")
                                   @DisplayName("Algorithm")
                                   @Summary("Compression algorithm to use")
                                   CompressionAlgorithm algorithm,
                                   @Optional(defaultValue = "3")
                                   @DisplayName("Compression Level")
                                   @Summary("Compression level (1-22)")
                                   int level) {
        LOGGER.debug("Starting directory compression: {} with algorithm {}", directoryPath, algorithm.getDisplayName());

        try {
            String cleanPath = directoryPath;
            if (cleanPath != null) {
                cleanPath = cleanPath.trim();
                if ((cleanPath.startsWith("\"") && cleanPath.endsWith("\"")) ||
                    (cleanPath.startsWith("'") && cleanPath.endsWith("'"))) {
                    cleanPath = cleanPath.substring(1, cleanPath.length() - 1);
                }
            }

            String normalizedPath = cleanPath.replace("/", File.separator);
            File directory = new File(normalizedPath);

            if (!directory.exists() || !directory.isDirectory()) {
                throw new IllegalArgumentException("Invalid directory path: " + normalizedPath +
                                                  " (absolute: " + directory.getAbsolutePath() + ")");
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            compressDirectoryRecursive(directory, directory, baos);
            byte[] directoryData = baos.toByteArray();

            byte[] compressedData = compressData(directoryData, algorithm, level);

            String outputPath = outputFilePath;
            if (outputPath == null || outputPath.isEmpty()) {
                outputPath = normalizedPath + ".archive." + algorithm.name().toLowerCase();
            } else {
                outputPath = outputPath.trim();
                if ((outputPath.startsWith("\"") && outputPath.endsWith("\"")) ||
                    (outputPath.startsWith("'") && outputPath.endsWith("'"))) {
                    outputPath = outputPath.substring(1, outputPath.length() - 1);
                }
                outputPath = outputPath.replace("/", File.separator);
            }

            Files.write(Paths.get(outputPath), compressedData);
            LOGGER.info("Directory compressed successfully: {} -> {}", normalizedPath, outputPath);

            return outputPath;
        } catch (IOException e) {
            LOGGER.error("Directory compression failed", e);
            throw new RuntimeException("Directory compression failed: " + e.getMessage(), e);
        }
    }

    private void compressDirectoryRecursive(File rootDir, File currentFile, ByteArrayOutputStream baos) throws IOException {
        if (currentFile.isDirectory()) {
            File[] files = currentFile.listFiles();
            if (files != null) {
                for (File file : files) {
                    compressDirectoryRecursive(rootDir, file, baos);
                }
            }
        } else {
            String relativePath = rootDir.toPath().relativize(currentFile.toPath()).toString();
            byte[] pathBytes = relativePath.getBytes("UTF-8");
            byte[] fileData = Files.readAllBytes(currentFile.toPath());

            baos.write(ByteBuffer.allocate(4).putInt(pathBytes.length).array());
            baos.write(pathBytes);
            baos.write(ByteBuffer.allocate(4).putInt(fileData.length).array());
            baos.write(fileData);
        }
    }

    private byte[] compressData(byte[] data, CompressionAlgorithm algorithm, int level) {
        switch (algorithm) {
            case ZSTD:
                return Zstd.compress(data, level);
            case LZ4:
                return compressLz4(data);
            case SNAPPY:
                return compressSnappy(data);
            case BROTLI:
                return compressBrotli(data, level);
            default:
                throw new IllegalArgumentException("Unsupported compression algorithm: " + algorithm);
        }
    }

    private byte[] decompressData(byte[] data, CompressionAlgorithm algorithm) {
        switch (algorithm) {
            case ZSTD:
                long size = Zstd.decompressedSize(data);
                if (size > Integer.MAX_VALUE) {
                    throw new IllegalArgumentException("Decompressed content is too large");
                }
                return Zstd.decompress(data, (int) size);
            case LZ4:
                return decompressLz4(data);
            case SNAPPY:
                return decompressSnappy(data);
            case BROTLI:
                return decompressBrotli(data);
            default:
                throw new IllegalArgumentException("Unsupported compression algorithm: " + algorithm);
        }
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

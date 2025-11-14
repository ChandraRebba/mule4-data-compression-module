# Compression Module - Multi-Algorithm Support

## Overview
The Compression Module supports multiple compression algorithms and file compression capabilities with GUI file picker support.

## Supported Compression Algorithms & File Extensions
1. **ZSTD (Zstandard)** - Default algorithm, high compression ratio. File extension: `.zstd`
2. **LZ4** - Fast compression/decompression. File extension: `.lz4`
3. **Snappy** - Optimized for speed. File extension: `.snappy`
4. **Brotli** - High compression ratio, web-optimized. File extension: `.brotli`

## Features

### 1. Payload Compression Operations
Located in `CompressionOperations.java`

#### Compress Operation
- **Parameters:**
  - `data` (byte[]): Content to compress
  - `algorithm` (CompressionAlgorithm): ZSTD, LZ4, SNAPPY, or BROTLI (default: ZSTD)
  - `level` (int): Compression level (default: 3, not applicable for all algorithms)
- **Returns:** Compressed byte array
- **Note:** Logs warning if compressed size exceeds original size

#### Decompress Operation
- **Parameters:**
  - `data` (byte[]): Compressed content
  - `algorithm` (CompressionAlgorithm): Must match compression algorithm
- **Returns:** Decompressed byte array
- **Note:** Original size is automatically stored in the compressed data

### 2. File Compression Operations
Located in `FileCompressionOperations.java`

#### Compress File
- **Parameters:**
  - `inputFilePath` (String): Path to file to compress
  - `outputFilePath` (String): Optional output path
  - `algorithm` (CompressionAlgorithm): Compression algorithm (default: ZSTD)
  - `level` (int): Compression level (default: 3, not applicable for all algorithms)
- **Returns:** Output file path

#### Decompress File
- **Parameters:**
  - `inputFilePath` (String): Path to compressed file
  - `outputFilePath` (String): Optional output path
  - `algorithm` (CompressionAlgorithm): Must match compression algorithm
- **Returns:** Output file path

#### Compress Directory
- **Parameters:**
  - `directoryPath` (String): Path to directory to compress
    - **Note:** Enter directory path manually (e.g., C:\\MyFolder or /home/user/myfolder)
  - `outputFilePath` (String): Optional output archive path
  - `algorithm` (CompressionAlgorithm): Compression algorithm (default: ZSTD)
  - `level` (int): Compression level (default: 3, not applicable for all algorithms)
- **Returns:** Output archive path

## Dependencies
- **ZSTD**: com.github.luben:zstd-jni:1.5.6-1
- **LZ4**: org.lz4:lz4-java:1.8.0
- **Snappy**: org.xerial.snappy:snappy-java:1.1.10.5
- **Brotli**: com.aayushatharva.brotli4j:brotli4j:1.16.0

## Usage Examples

### MuleSoft Flow XML Examples

#### Compress with ZSTD
```xml
<compression:compress algorithm="ZSTD" level="3"/>

#### Compress with LZ4
```xml
<compression:compress algorithm="LZ4" level="3"/>
```

#### Compress with Snappy
```xml
<compression:compress algorithm="SNAPPY"/>
```

#### Compress with Brotli
```xml
<compression:compress algorithm="BROTLI" level="5"/>
```

#### Decompress (works for all algorithms)
```xml
<compression:decompress algorithm="LZ4"/>
```

#### Compress File 
```xml
<compression:compress-file 
    inputFilePath="/path/to/file.txt" 
    outputFilePath="/path/to/file.txt.zstd"
    algorithm="ZSTD"
    level="5"/>
```

#### Compress Directory
```xml
<compression:compress-directory 
    directoryPath="/path/to/directory"
    outputFilePath="/path/to/archive.zstd"
    algorithm="BROTLI"
    level="7"/>
```

## Algorithm Characteristics

| Algorithm | Speed | Compression Ratio | Best For |
|-----------|-------|-------------------|----------|
| ZSTD | Medium | High | General purpose, balanced |
| LZ4 | Very Fast | Medium | Real-time processing |
| Snappy | Fast | Medium | High throughput scenarios |
| Brotli | Slow | Very High | Web content, static files |

## Important Notes

### Comparison of Compression Algorithms
- **ZSTD**: Offers a balance between speed and compression ratio, making it suitable for general-purpose use.
- **LZ4**: Prioritizes speed over compression ratio, ideal for real-time processing needs.
- **Snappy**: Optimized for high throughput scenarios with moderate compression efficiency.
- **Brotli**: Provides the highest compression ratio but at the cost of slower processing, best suited for web content and static files.

### Compression Behavior
- **Already Compressed Data:** Compression algorithms cannot compress already-compressed data (e.g., JPEG, PNG, ZIP, MP4). Attempting to compress such files will result in a larger output size due to compression metadata overhead.
- **Size Warnings:** The module logs warnings when compressed output is larger than the input, indicating the data may already be compressed or is not suitable for compression.
- **Best Candidates for Compression:**
  - Text files (TXT, CSV, JSON, XML, HTML)
  - Log files
  - Uncompressed images (BMP, TIFF)
  - Source code files
  - Database dumps
- **Avoid Compressing:**
  - Already compressed images (JPEG, PNG, GIF, WebP)
  - Compressed archives (ZIP, RAR, 7Z, TAR.GZ)
  - Compressed video (MP4, AVI, MKV)
  - Compressed audio (MP3, AAC, OGG)

### Technical Details
- Original size is automatically stored in compressed data for all algorithms
- Brotli quality ranges from 0-11 (mapped from compression level)
- ZSTD compression level ranges from 1-22 (default: 3)
- File operations automatically append algorithm extension if output path not specified
- Directory compression creates a single archive file with all contents
- XML prefix: `compression`
- Compression ratio and size information is logged for all operations

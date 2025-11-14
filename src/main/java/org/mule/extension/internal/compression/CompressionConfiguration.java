package org.mule.extension.internal.compression;

import org.mule.runtime.extension.api.annotation.Operations;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Operations({CompressionOperations.class, FileCompressionOperations.class})
public class CompressionConfiguration {

    private static final Logger LOGGER = LoggerFactory.getLogger(CompressionConfiguration.class);
}

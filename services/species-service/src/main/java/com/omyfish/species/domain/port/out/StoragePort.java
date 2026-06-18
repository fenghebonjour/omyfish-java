package com.omyfish.species.domain.port.out;

import java.io.InputStream;

public interface StoragePort {
    String store(InputStream data, long size, String contentType, String originalFilename);
}

package com.omyfish.shop.domain.port.out;

import com.omyfish.shop.domain.model.valueobject.StoredImage;

import java.io.InputStream;

public interface ImageStoragePort {

    // Returns an opaque storage key — never a browsable URL. Product.imageUrl points at this
    // service's own serve-image endpoint instead, so MinIO itself never needs to be exposed.
    String store(InputStream data, long size, String contentType, String originalFilename);

    StoredImage load(String key);
}

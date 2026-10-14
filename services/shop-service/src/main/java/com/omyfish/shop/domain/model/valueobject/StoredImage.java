package com.omyfish.shop.domain.model.valueobject;

import java.io.InputStream;

public record StoredImage(InputStream data, String contentType) {}

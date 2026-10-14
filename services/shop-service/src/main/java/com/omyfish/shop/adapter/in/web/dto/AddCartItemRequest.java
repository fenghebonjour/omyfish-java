package com.omyfish.shop.adapter.in.web.dto;

import java.util.UUID;

public record AddCartItemRequest(UUID productId, int quantity) {
}

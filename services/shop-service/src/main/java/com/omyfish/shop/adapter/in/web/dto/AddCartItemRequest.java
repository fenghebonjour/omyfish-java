package com.omyfish.shop.adapter.in.web.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.UUID;

public record AddCartItemRequest(@NotNull UUID productId, @Positive int quantity) {
}

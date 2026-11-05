package com.omyfish.shop.adapter.in.web.dto;

import jakarta.validation.constraints.PositiveOrZero;

public record UpdateCartItemRequest(@PositiveOrZero int quantity) {
}

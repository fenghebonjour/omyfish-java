package com.omyfish.shop.domain.port.out;

import com.omyfish.shop.domain.model.Product;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductRepository {
    List<Product> findAllActive();
    Optional<Product> findById(UUID id);
    Product save(Product product);

    // A single atomic conditional UPDATE instead of load->decrement->save — under concurrent
    // checkouts on the same hot item, two callers loading the same stockQuantity would both
    // pass an in-memory check and both decrement, overselling it. Returns false (no rows
    // matched) when quantity exceeds what's actually in stock right now.
    boolean tryDecreaseStock(UUID id, int quantity);
}

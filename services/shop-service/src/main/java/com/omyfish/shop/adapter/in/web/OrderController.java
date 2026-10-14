package com.omyfish.shop.adapter.in.web;

import com.omyfish.shop.adapter.in.web.dto.OrderResponse;
import com.omyfish.shop.adapter.in.web.dto.ReturnRequest;
import com.omyfish.shop.domain.port.in.ApproveReturnUseCase;
import com.omyfish.shop.domain.port.in.GetOrderUseCase;
import com.omyfish.shop.domain.port.in.ListAllOrdersUseCase;
import com.omyfish.shop.domain.port.in.ListOrdersUseCase;
import com.omyfish.shop.domain.port.in.LookupGuestOrderUseCase;
import com.omyfish.shop.domain.port.in.RequestReturnUseCase;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/shop/orders")
public class OrderController {

    private final ListOrdersUseCase listOrdersUseCase;
    private final GetOrderUseCase getOrderUseCase;
    private final RequestReturnUseCase requestReturnUseCase;
    private final ApproveReturnUseCase approveReturnUseCase;
    private final LookupGuestOrderUseCase lookupGuestOrderUseCase;
    private final ListAllOrdersUseCase listAllOrdersUseCase;

    public OrderController(
        ListOrdersUseCase listOrdersUseCase, GetOrderUseCase getOrderUseCase,
        RequestReturnUseCase requestReturnUseCase, ApproveReturnUseCase approveReturnUseCase,
        LookupGuestOrderUseCase lookupGuestOrderUseCase, ListAllOrdersUseCase listAllOrdersUseCase
    ) {
        this.listOrdersUseCase = listOrdersUseCase;
        this.getOrderUseCase = getOrderUseCase;
        this.requestReturnUseCase = requestReturnUseCase;
        this.approveReturnUseCase = approveReturnUseCase;
        this.lookupGuestOrderUseCase = lookupGuestOrderUseCase;
        this.listAllOrdersUseCase = listAllOrdersUseCase;
    }

    // Public — guest order tracking by orderId+email, no account needed. Whitelisted in the
    // gateway AuthFilter; must stay registered before the "/{id}" mapping below so the literal
    // "lookup" segment doesn't get captured as a path variable.
    @GetMapping("/lookup")
    public ResponseEntity<OrderResponse> lookup(
        @RequestParam UUID orderId, @RequestParam String email
    ) {
        return ResponseEntity.ok(OrderResponse.from(lookupGuestOrderUseCase.lookup(orderId, email)));
    }

    // Admin-only; lists every order regardless of owner (newest first) so an admin can find
    // one — e.g. to approve a return — without already having its id from a support request.
    // Must stay registered before "/{id}" below for the same reason "lookup" does.
    @GetMapping("/admin")
    public ResponseEntity<List<OrderResponse>> listAllForAdmin(@RequestHeader("X-User-Role") String userRoleHeader) {
        if (!"ADMIN".equalsIgnoreCase(userRoleHeader)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin only");
        }
        var orders = listAllOrdersUseCase.listAll().stream().map(OrderResponse::from).toList();
        return ResponseEntity.ok(orders);
    }

    @GetMapping
    public ResponseEntity<List<OrderResponse>> list(@RequestHeader("X-User-Id") String userIdHeader) {
        var orders = listOrdersUseCase.listByUser(UUID.fromString(userIdHeader)).stream()
            .map(OrderResponse::from).toList();
        return ResponseEntity.ok(orders);
    }

    @GetMapping("/{id}")
    public ResponseEntity<OrderResponse> get(
        @PathVariable("id") UUID id, @RequestHeader("X-User-Id") String userIdHeader
    ) {
        return ResponseEntity.ok(OrderResponse.from(getOrderUseCase.get(id, UUID.fromString(userIdHeader))));
    }

    @PostMapping("/{id}/returns")
    public ResponseEntity<OrderResponse> requestReturn(
        @PathVariable("id") UUID id, @RequestHeader("X-User-Id") String userIdHeader,
        @RequestBody ReturnRequest request
    ) {
        var order = requestReturnUseCase.requestReturn(id, UUID.fromString(userIdHeader), request.reason());
        return ResponseEntity.ok(OrderResponse.from(order));
    }

    @PatchMapping("/{id}/returns/approve")
    public ResponseEntity<OrderResponse> approveReturn(
        @PathVariable("id") UUID id, @RequestHeader("X-User-Role") String userRoleHeader
    ) {
        if (!"ADMIN".equalsIgnoreCase(userRoleHeader)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin only");
        }
        return ResponseEntity.ok(OrderResponse.from(approveReturnUseCase.approveReturn(id)));
    }
}

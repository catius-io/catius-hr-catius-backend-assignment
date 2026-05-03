package com.catius.inventory.controller;

import com.catius.inventory.controller.dto.InventoryResponse;
import com.catius.inventory.controller.dto.ReleaseRequest;
import com.catius.inventory.controller.dto.ReleaseResponse;
import com.catius.inventory.controller.dto.ReservationResponse;
import com.catius.inventory.controller.dto.ReserveRequest;
import com.catius.inventory.domain.Reservation;
import com.catius.inventory.service.InventoryReservationService;
import com.catius.inventory.service.ReleaseOutcome;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/inventory")
@RequiredArgsConstructor
public class InventoryController {

    private final InventoryReservationService service;

    @GetMapping("/{productId}")
    public InventoryResponse getInventory(@PathVariable long productId) {
        return InventoryResponse.from(service.getInventory(productId));
    }

    @PostMapping("/reserve")
    public ReservationResponse reserve(@Valid @RequestBody ReserveRequest request) {
        Reservation r = service.reserve(request.orderId(), request.productId(), request.quantity());
        return ReservationResponse.from(r);
    }

    @PostMapping("/release")
    public ReleaseResponse release(@Valid @RequestBody ReleaseRequest request) {
        ReleaseOutcome outcome = service.release(request.orderId(), request.productId());
        return ReleaseResponse.of(request.orderId(), request.productId(), outcome);
    }
}

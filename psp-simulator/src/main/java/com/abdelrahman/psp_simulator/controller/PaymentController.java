package com.abdelrahman.psp_simulator.controller;

import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.abdelrahman.psp_simulator.dto.AuthorizeRequest;
import com.abdelrahman.psp_simulator.dto.PaymentResponse;
import com.abdelrahman.psp_simulator.dto.Simulation;
import com.abdelrahman.psp_simulator.service.PaymentService;
import com.abdelrahman.psp_simulator.web.ApiHeaders;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/payments")
public class PaymentController {

    private final PaymentService payments;

    public PaymentController(PaymentService payments) {
        this.payments = payments;
    }

    @PostMapping
    public PaymentResponse authorize(
            @RequestHeader(ApiHeaders.IDEMPOTENCY_KEY) String idempotencyKey,
            @RequestHeader(value = ApiHeaders.SIMULATE, required = false) String simulate,
            @Valid @RequestBody AuthorizeRequest request) {
        return payments.authorize(request, idempotencyKey, Simulation.parse(simulate));
    }

    @PostMapping("/{id}/capture")
    public PaymentResponse capture(
            @PathVariable UUID id,
            @RequestHeader(ApiHeaders.IDEMPOTENCY_KEY) String idempotencyKey,
            @RequestHeader(value = ApiHeaders.SIMULATE, required = false) String simulate) {
        return payments.capture(id, idempotencyKey, Simulation.parse(simulate));
    }

    @PostMapping("/{id}/void")
    public PaymentResponse voidPayment(
            @PathVariable UUID id,
            @RequestHeader(ApiHeaders.IDEMPOTENCY_KEY) String idempotencyKey,
            @RequestHeader(value = ApiHeaders.SIMULATE, required = false) String simulate) {
        return payments.voidPayment(id, idempotencyKey, Simulation.parse(simulate));
    }

    @GetMapping("/{id}")
    public PaymentResponse getById(
            @PathVariable UUID id,
            @RequestHeader(value = ApiHeaders.SIMULATE, required = false) String simulate) {
        return payments.queryById(id, Simulation.parse(simulate));
    }

    @GetMapping
    public PaymentResponse getByKey(
            @RequestParam("key") String key,
            @RequestHeader(value = ApiHeaders.SIMULATE, required = false) String simulate) {
        return payments.queryByKey(key, Simulation.parse(simulate));
    }
}

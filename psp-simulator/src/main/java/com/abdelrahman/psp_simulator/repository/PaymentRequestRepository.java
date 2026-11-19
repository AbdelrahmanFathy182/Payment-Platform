package com.abdelrahman.psp_simulator.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.abdelrahman.psp_simulator.domain.PaymentRequest;

public interface PaymentRequestRepository extends JpaRepository<PaymentRequest, UUID> {

    Optional<PaymentRequest> findByIdempotencyKey(String idempotencyKey);

    @Query("select r from PaymentRequest r join fetch r.payment where r.idempotencyKey = :idempotencyKey")
    Optional<PaymentRequest> findByIdempotencyKeyWithPayment(@Param("idempotencyKey") String idempotencyKey);
}

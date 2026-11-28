package com.abdelrahman.orchestrator.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.abdelrahman.orchestrator.domain.Payment;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {
}

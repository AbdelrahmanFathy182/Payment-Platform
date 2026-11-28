package com.abdelrahman.orchestrator.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.abdelrahman.orchestrator.domain.PaymentStep;

public interface PaymentStepRepository extends JpaRepository<PaymentStep, UUID> {
}

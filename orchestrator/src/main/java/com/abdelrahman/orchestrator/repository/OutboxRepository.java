package com.abdelrahman.orchestrator.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.abdelrahman.orchestrator.domain.OutboxEvent;

public interface OutboxRepository extends JpaRepository<OutboxEvent, UUID> {
}

package com.abdelrahman.orchestrator.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.abdelrahman.orchestrator.domain.ClientIdempotency;
import com.abdelrahman.orchestrator.domain.ClientIdempotencyId;

public interface ClientIdempotencyRepository extends JpaRepository<ClientIdempotency, ClientIdempotencyId> {
}

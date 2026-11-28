package com.abdelrahman.orchestrator.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.abdelrahman.orchestrator.domain.Attempt;
import com.abdelrahman.orchestrator.domain.AttemptId;

public interface AttemptRepository extends JpaRepository<Attempt, AttemptId> {
}

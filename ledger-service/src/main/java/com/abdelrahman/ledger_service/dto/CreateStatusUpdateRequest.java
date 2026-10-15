package com.abdelrahman.ledger_service.dto;

import jakarta.validation.constraints.NotNull;

public record CreateStatusUpdateRequest(@NotNull Boolean active) {
}
package com.abdelrahman.ledger_service.dto;

import java.util.List;
import java.util.UUID;

/**
 * EntriesResponse
 */
public record EntriesResponse(
        UUID accountId,
        List<EntryDTO> entries) {
}
package com.abdelrahman.orchestrator.domain;

public enum PaymentStage {
    RECEIVED,
    AUTHORIZE,
    CAPTURE,
    VOID,
    LEDGER_POST
}

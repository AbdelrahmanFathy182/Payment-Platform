package com.abdelrahman.orchestrator.domain;

public enum TerminalOutcome {
    SETTLED,
    DECLINED,
    VOIDED,
    ABANDONED,
    RETRIES_EXHAUSTED,
    NEEDS_ATTENTION
}

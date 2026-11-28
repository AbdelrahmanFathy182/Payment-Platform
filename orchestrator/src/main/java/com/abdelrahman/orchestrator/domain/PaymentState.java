package com.abdelrahman.orchestrator.domain;

public record PaymentState(PaymentStage stage, StepStatus status, TerminalOutcome outcome) {

    public static final PaymentState RECEIVED = of(PaymentStage.RECEIVED, StepStatus.PENDING);
    public static final PaymentState AUTHORIZE_PENDING = of(PaymentStage.AUTHORIZE, StepStatus.PENDING);
    public static final PaymentState AUTHORIZE_IN_FLIGHT = of(PaymentStage.AUTHORIZE, StepStatus.IN_FLIGHT);
    public static final PaymentState AUTHORIZE_IN_DUBT = of(PaymentStage.AUTHORIZE, StepStatus.IN_DUBT);
    public static final PaymentState AUTHORIZE_DONE = of(PaymentStage.AUTHORIZE, StepStatus.DONE);
    public static final PaymentState AUTHORIZE_FAILED = of(PaymentStage.AUTHORIZE, StepStatus.FAILED);

    public static final PaymentState CAPTURE_PENDING = of(PaymentStage.CAPTURE, StepStatus.PENDING);
    public static final PaymentState CAPTURE_IN_FLIGHT = of(PaymentStage.CAPTURE, StepStatus.IN_FLIGHT);
    public static final PaymentState CAPTURE_IN_DUBT = of(PaymentStage.CAPTURE, StepStatus.IN_DUBT);
    public static final PaymentState CAPTURE_DONE = of(PaymentStage.CAPTURE, StepStatus.DONE);
    public static final PaymentState CAPTURE_FAILED = of(PaymentStage.CAPTURE, StepStatus.FAILED);

    public static final PaymentState VOID_PENDING = of(PaymentStage.VOID, StepStatus.PENDING);
    public static final PaymentState VOID_IN_FLIGHT = of(PaymentStage.VOID, StepStatus.IN_FLIGHT);
    public static final PaymentState VOID_IN_DUBT = of(PaymentStage.VOID, StepStatus.IN_DUBT);
    public static final PaymentState VOID_DONE = of(PaymentStage.VOID, StepStatus.DONE);

    public static final PaymentState LEDGER_POST_PENDING = of(PaymentStage.LEDGER_POST, StepStatus.PENDING);
    public static final PaymentState LEDGER_POST_IN_FLIGHT = of(PaymentStage.LEDGER_POST, StepStatus.IN_FLIGHT);
    public static final PaymentState LEDGER_POST_IN_DUBT = of(PaymentStage.LEDGER_POST, StepStatus.IN_DUBT);
    public static final PaymentState LEDGER_POST_DONE = of(PaymentStage.LEDGER_POST, StepStatus.DONE);
    public static final PaymentState LEDGER_POST_FAILED = of(PaymentStage.LEDGER_POST, StepStatus.FAILED);

    public static final PaymentState SETTLED = terminal(
            PaymentStage.LEDGER_POST, StepStatus.DONE, TerminalOutcome.SETTLED);
    public static final PaymentState DECLINED = terminal(
            PaymentStage.AUTHORIZE, StepStatus.FAILED, TerminalOutcome.DECLINED);
    public static final PaymentState VOIDED = terminal(
            PaymentStage.VOID, StepStatus.DONE, TerminalOutcome.VOIDED);

    public PaymentState(PaymentStage stage, StepStatus status) {
        this(stage, status, null);
    }

    public static PaymentState of(PaymentStage stage, StepStatus status) {
        return new PaymentState(stage, status);
    }

    public static PaymentState terminal(PaymentStage stage, StepStatus status, TerminalOutcome outcome) {
        return new PaymentState(stage, status, outcome);
    }

    public static PaymentState abandoned(PaymentStage stage) {
        return terminal(stage, StepStatus.IN_DUBT, TerminalOutcome.ABANDONED);
    }

    public static PaymentState retriesExhausted(PaymentStage stage, StepStatus status) {
        return terminal(stage, status, TerminalOutcome.RETRIES_EXHAUSTED);
    }

    public static PaymentState needsAttention(PaymentStage stage, StepStatus status) {
        return terminal(stage, status, TerminalOutcome.NEEDS_ATTENTION);
    }

    public boolean terminal() {
        return outcome != null;
    }
}

package com.abdelrahman.ledger_service.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.abdelrahman.ledger_service.dto.TransactionRequest;
import com.abdelrahman.ledger_service.dto.TransactionResponse;
import com.abdelrahman.ledger_service.service.TransactionService;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/Transactions")
public class TransactionController {
    private final TransactionService service;

    public TransactionController(TransactionService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<TransactionResponse> postTransaction(@Valid @RequestBody TransactionRequest request) {
        TransactionService.CreateResult result = service.createTransaction(request);
        TransactionResponse body = TransactionResponse.from(result.transaction());
        return ResponseEntity.status(result.created() ? 201 : 200).body(body);
    }
    @PostMapping("/{id}/reversal")
    public ResponseEntity<TransactionResponse> postReversal(@PathVariable UUID id,@RequestHeader("Idempotency-Key") String idempotencyKey){
    TransactionService.ReversalResult result= service.reverseTransaction(id, idempotencyKey);
        TransactionResponse body = TransactionResponse.from(result.transaction());
        return ResponseEntity.status(200).body(body);    
    }
    @GetMapping("/{id}")
    public ResponseEntity<TransactionResponse> getMethodName(@PathVariable  UUID id) {
        TransactionService.GetResult result= service.getTransaction(id);
        TransactionResponse body = TransactionResponse.from(result.transaction());
        return ResponseEntity.status(200).body(body);
    }
    

}
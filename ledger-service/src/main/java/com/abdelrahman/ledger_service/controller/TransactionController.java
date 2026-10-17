package com.abdelrahman.ledger_service.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.abdelrahman.ledger_service.dto.TransactionRequest;
import com.abdelrahman.ledger_service.dto.TransactionResponse;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;


@RestController 
@RequestMapping("/api/v1/Transactions")
public class TransactionController {

    @PostMapping
    public ResponseEntity<TransactionResponse> postTransaction(@Valid @RequestBody TransactionRequest request) {
        
        return null;
    }
    
}
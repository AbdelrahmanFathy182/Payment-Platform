package com.abdelrahman.ledger_service.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.abdelrahman.ledger_service.dto.AccountResponse;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import jakarta.validation.Valid;
import com.abdelrahman.ledger_service.service.AccountService;
import com.abdelrahman.ledger_service.dto.*;;


@RestController
@RequestMapping("/api/v1/accounts")
public class AccountController {

      
    private final AccountService service;

    public AccountController(AccountService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<AccountResponse> create(@Valid @RequestBody CreateAccountRequest request) {
        AccountService.Result result = service.create(request);
        AccountResponse body = AccountResponse.from(result.account());
        return ResponseEntity.status(result.created() ? 201 : 200).body(body);
    }
    
}

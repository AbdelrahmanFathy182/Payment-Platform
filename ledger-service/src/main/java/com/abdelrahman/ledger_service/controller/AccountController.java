package com.abdelrahman.ledger_service.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import jakarta.validation.Valid;
import com.abdelrahman.ledger_service.service.AccountService;
import com.abdelrahman.ledger_service.dto.*;
import org.springframework.web.bind.annotation.GetMapping;


@RestController
@RequestMapping("/api/v1/accounts")
public class AccountController {

    private final AccountService service;

    public AccountController(AccountService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<AccountResponse> create(@Valid @RequestBody CreateAccountRequest request) {
        AccountService.CreateResult result = service.create(request);
        AccountResponse body = AccountResponse.from(result.account());
        return ResponseEntity.status(result.created() ? 201 : 200).body(body);
    }

    @PatchMapping("/{name}")
    public ResponseEntity<AccountResponse> updareAccountStatus(@PathVariable String name, @Valid @RequestBody CreateStatusUpdateRequest request) {
        AccountService.UpdateResult result = service.updateStatus(name, request);
        AccountResponse body = AccountResponse.from(result.account());        
        return ResponseEntity.status(result.updated() ? 201 : 200).body(body);
    }
    @GetMapping("/{name}")
    public ResponseEntity<AccountResponse> getMethodName(@PathVariable String name) {
        AccountService.GetResult result = service.getAccount(name);
        AccountResponse body= AccountResponse.from(result.account());
        return ResponseEntity.status(result.found() ? 201:200).body(body);
    }
    
}

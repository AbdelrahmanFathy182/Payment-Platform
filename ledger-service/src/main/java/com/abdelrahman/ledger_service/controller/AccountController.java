package com.abdelrahman.ledger_service.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.abdelrahman.ledger_service.domain.Account;


@RestController
@RequestMapping("/api/v1/auth")
public class AccountController {

    @GetMapping()
    public Account createAccount() {
        return null;
    }
}
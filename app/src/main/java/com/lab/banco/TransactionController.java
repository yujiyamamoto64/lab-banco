package com.lab.banco;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TransactionController {

    private final MockTransactionService service;

    public TransactionController(MockTransactionService service) {
        this.service = service;
    }

    @GetMapping("/transacoes")
    public List<String> listar() {
        return service.getTransactions();
    }
}

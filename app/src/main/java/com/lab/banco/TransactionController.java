package com.lab.banco;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TransactionController {

    private final TransferTransactionRepository transferTransactionRepository;
    private final AccountRepository accountRepository;

    public TransactionController(
            TransferTransactionRepository transferTransactionRepository,
            AccountRepository accountRepository) {
        this.transferTransactionRepository = transferTransactionRepository;
        this.accountRepository = accountRepository;
    }

    @GetMapping("/transacoes")
    public List<TransferResponse> listTransfersPt() {
        return listTransfers();
    }

    @GetMapping("/transfers")
    public List<TransferResponse> listTransfers() {
        List<TransferTransaction> transfers = transferTransactionRepository.findTop50ByOrderByOccurredAtDesc();
        if (transfers.isEmpty()) {
            return List.of();
        }

        Set<Long> accountIds = new HashSet<>();
        for (TransferTransaction transfer : transfers) {
            accountIds.add(transfer.getOriginAccountId());
            accountIds.add(transfer.getDestinationAccountId());
        }

        Map<Long, String> accountNamesById = accountRepository.findAllById(accountIds)
                .stream()
                .collect(Collectors.toMap(Account::getId, Account::getName));

        return transfers.stream()
                .map(transfer -> new TransferResponse(
                        transfer.getId(),
                        transfer.getOriginAccountId(),
                        accountNamesById.getOrDefault(transfer.getOriginAccountId(), "Unknown"),
                        transfer.getDestinationAccountId(),
                        accountNamesById.getOrDefault(transfer.getDestinationAccountId(), "Unknown"),
                        transfer.getAmount(),
                        transfer.getOccurredAt()))
                .toList();
    }

    public record TransferResponse(
            Long id,
            Long originAccountId,
            String originAccountName,
            Long destinationAccountId,
            String destinationAccountName,
            BigDecimal amount,
            LocalDateTime occurredAt) {
    }
}

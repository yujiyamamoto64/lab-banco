package com.lab.banco;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransferService {

    private final AccountRepository accountRepository;
    private final TransferTransactionRepository transferTransactionRepository;

    public TransferService(
            AccountRepository accountRepository,
            TransferTransactionRepository transferTransactionRepository) {
        this.accountRepository = accountRepository;
        this.transferTransactionRepository = transferTransactionRepository;
    }

    @Transactional
    public void transfer(Long originAccountId, Long destinationAccountId, BigDecimal amount) {
        validateTransferInput(originAccountId, destinationAccountId, amount);

        Account origin = accountRepository.findById(originAccountId)
                .orElseThrow(() -> new IllegalArgumentException("Origin account not found"));

        Account destination = accountRepository.findById(destinationAccountId)
                .orElseThrow(() -> new IllegalArgumentException("Destination account not found"));

        if (origin.getBalance().compareTo(amount) < 0) {
            throw new IllegalStateException("Insufficient balance");
        }

        origin.setBalance(origin.getBalance().subtract(amount));
        destination.setBalance(destination.getBalance().add(amount));

        accountRepository.save(origin);
        accountRepository.save(destination);

        TransferTransaction transaction = new TransferTransaction();
        transaction.setOriginAccountId(originAccountId);
        transaction.setDestinationAccountId(destinationAccountId);
        transaction.setAmount(amount);
        transaction.setOccurredAt(LocalDateTime.now());

        transferTransactionRepository.save(transaction);
    }

    private void validateTransferInput(Long originAccountId, Long destinationAccountId, BigDecimal amount) {
        if (originAccountId == null || destinationAccountId == null) {
            throw new IllegalArgumentException("Origin and destination account ids are required");
        }

        if (originAccountId.equals(destinationAccountId)) {
            throw new IllegalArgumentException("Origin and destination accounts must be different");
        }

        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("Transfer amount must be greater than zero");
        }
    }
}

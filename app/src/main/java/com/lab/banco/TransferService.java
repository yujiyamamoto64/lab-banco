package com.lab.banco;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransferService {

    private final AccountRepository accountRepository;
    private final TransferTransactionRepository transferTransactionRepository;
    private final boolean chaosEnabled;
    private final long chaosSleepBeforeUpdateMs;
    private final double chaosFailAfterOriginUpdateProbability;

    public TransferService(
            AccountRepository accountRepository,
            TransferTransactionRepository transferTransactionRepository,
            @Value("${app.chaos.enabled:false}") boolean chaosEnabled,
            @Value("${app.chaos.sleep-before-update-ms:0}") long chaosSleepBeforeUpdateMs,
            @Value("${app.chaos.fail-after-origin-update-probability:0.0}") double chaosFailAfterOriginUpdateProbability) {
        this.accountRepository = accountRepository;
        this.transferTransactionRepository = transferTransactionRepository;
        this.chaosEnabled = chaosEnabled;
        this.chaosSleepBeforeUpdateMs = Math.max(0, chaosSleepBeforeUpdateMs);
        this.chaosFailAfterOriginUpdateProbability = Math.max(0.0, Math.min(1.0, chaosFailAfterOriginUpdateProbability));
    }

    @Transactional(noRollbackFor = ChaosInconsistencyException.class)
    public void transfer(Long originAccountId, Long destinationAccountId, BigDecimal amount) {
        transfer(originAccountId, destinationAccountId, amount, TransferCategory.MOCK);
    }

    @Transactional(noRollbackFor = ChaosInconsistencyException.class)
    public void transfer(
            Long originAccountId,
            Long destinationAccountId,
            BigDecimal amount,
            TransferCategory category) {
        validateTransferInput(originAccountId, destinationAccountId, amount);

        Account origin = accountRepository.findById(originAccountId)
                .orElseThrow(() -> new IllegalArgumentException("Origin account not found"));

        Account destination = accountRepository.findById(destinationAccountId)
                .orElseThrow(() -> new IllegalArgumentException("Destination account not found"));

        if (origin.getBalance().compareTo(amount) < 0) {
            throw new IllegalStateException("Insufficient balance");
        }

        maybeSleepBeforeUpdate();

        origin.setBalance(origin.getBalance().subtract(amount));
        destination.setBalance(destination.getBalance().add(amount));

        accountRepository.save(origin);

        if (shouldInjectFailureAfterOriginUpdate()) {
            throw new ChaosInconsistencyException(
                    "Chaos mode injected failure after origin update (partial commit simulation)");
        }

        accountRepository.save(destination);

        TransferTransaction transaction = new TransferTransaction();
        transaction.setOriginAccountId(originAccountId);
        transaction.setDestinationAccountId(destinationAccountId);
        transaction.setAmount(amount);
        transaction.setOccurredAt(LocalDateTime.now());
        transaction.setCategory(category == null ? TransferCategory.MOCK : category);

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

    private void maybeSleepBeforeUpdate() {
        if (!chaosEnabled || chaosSleepBeforeUpdateMs <= 0) {
            return;
        }

        try {
            Thread.sleep(chaosSleepBeforeUpdateMs);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Transfer interrupted during chaos sleep", ex);
        }
    }

    private boolean shouldInjectFailureAfterOriginUpdate() {
        if (!chaosEnabled || chaosFailAfterOriginUpdateProbability <= 0) {
            return false;
        }

        return ThreadLocalRandom.current().nextDouble() < chaosFailAfterOriginUpdateProbability;
    }
}

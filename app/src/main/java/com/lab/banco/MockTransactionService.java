package com.lab.banco;

import java.math.BigDecimal;
import java.util.Random;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class MockTransactionService {

    private static final String ORIGIN_ACCOUNT_NAME = "Joao";
    private static final String DESTINATION_ACCOUNT_NAME = "Maria";
    private static final int MAX_TRANSFER_AMOUNT = 1000;
    private static final int MIN_TRANSFER_AMOUNT = 1;
    private static final long INITIAL_SEED_BALANCE = 5000L;
    private static final Logger LOG = LoggerFactory.getLogger(MockTransactionService.class);

    private final Random random = new Random();
    private final TransferService transferService;
    private final AccountRepository accountRepository;
    private final BigDecimal minimumOriginBalance;
    private final BigDecimal rebalanceTargetBalance;

    public MockTransactionService(
            TransferService transferService,
            AccountRepository accountRepository,
            @Value("${app.mock-transfer.minimum-origin-balance:1000}") BigDecimal minimumOriginBalance,
            @Value("${app.mock-transfer.rebalance-target-balance:5000}") BigDecimal rebalanceTargetBalance) {
        this.transferService = transferService;
        this.accountRepository = accountRepository;
        this.minimumOriginBalance = minimumOriginBalance;
        this.rebalanceTargetBalance = rebalanceTargetBalance;
    }

    @Scheduled(fixedRateString = "${app.mock-transfer.fixed-rate-ms:5000}",
            initialDelayString = "${app.mock-transfer.initial-delay-ms:10000}")
    public void generateMockTransfer() {
        try {
            Account origin = ensureAccount(ORIGIN_ACCOUNT_NAME);
            Account destination = ensureAccount(DESTINATION_ACCOUNT_NAME);

            if (tryRebalanceIfNeeded(origin, destination)) {
                return;
            }

            int maxAmount = origin.getBalance()
                    .min(BigDecimal.valueOf(MAX_TRANSFER_AMOUNT))
                    .intValue();

            if (maxAmount < MIN_TRANSFER_AMOUNT) {
                LOG.info("Skipping mock transfer because {} has insufficient balance: {}",
                        origin.getName(), origin.getBalance());
                return;
            }

            BigDecimal amount = BigDecimal.valueOf(random.nextInt(maxAmount) + MIN_TRANSFER_AMOUNT);
            transferService.transfer(origin.getId(), destination.getId(), amount, TransferCategory.MOCK);
            LOG.info("Mock transfer persisted: {}({}) -> {}({}) amount {}",
                    origin.getName(), origin.getId(), destination.getName(), destination.getId(), amount);
        } catch (Exception ex) {
            LOG.warn("Failed to generate mock transfer", ex);
        }
    }

    private boolean tryRebalanceIfNeeded(Account origin, Account destination) {
        if (origin.getBalance().compareTo(minimumOriginBalance) >= 0) {
            return false;
        }

        BigDecimal desiredTopUp = rebalanceTargetBalance.subtract(origin.getBalance());
        if (desiredTopUp.compareTo(BigDecimal.ZERO) <= 0) {
            return false;
        }

        BigDecimal topUpAmount = desiredTopUp.min(destination.getBalance());
        if (topUpAmount.compareTo(BigDecimal.ZERO) <= 0) {
            LOG.warn("Cannot rebalance {} because {} has insufficient balance. originBalance={}, destinationBalance={}",
                    origin.getName(), destination.getName(), origin.getBalance(), destination.getBalance());
            return false;
        }

        transferService.transfer(
                destination.getId(),
                origin.getId(),
                topUpAmount,
                TransferCategory.REBALANCE);

        LOG.info("Rebalance transfer persisted: {}({}) -> {}({}) amount {}",
                destination.getName(), destination.getId(), origin.getName(), origin.getId(), topUpAmount);
        return true;
    }

    private Account ensureAccount(String accountName) {
        return accountRepository.findByNameIgnoreCase(accountName)
                .orElseGet(() -> {
                    Account account = new Account();
                    account.setName(accountName);
                    account.setBalance(BigDecimal.valueOf(INITIAL_SEED_BALANCE));
                    return accountRepository.save(account);
                });
    }
}

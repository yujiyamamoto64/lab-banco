package com.lab.banco;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class MockTransactionService {

    private static final int MAX_TRANSFER_AMOUNT = 1000;
    private static final int MIN_TRANSFER_AMOUNT = 1;
    private static final int MIN_ACCOUNT_COUNT = 2;
    private static final long INITIAL_SEED_BALANCE = 5000L;
    private static final Logger LOG = LoggerFactory.getLogger(MockTransactionService.class);

    private final Random random = new Random();
    private final TransferService transferService;
    private final AccountRepository accountRepository;

    private final List<String> seedNames = List.of(
            "Maria", "Joao", "Pedro", "Ana", "Carlos", "Bruno", "Fernanda");

    public MockTransactionService(TransferService transferService, AccountRepository accountRepository) {
        this.transferService = transferService;
        this.accountRepository = accountRepository;
    }

    @Scheduled(fixedRate = 5000, initialDelay = 10000)
    public void generateMockTransfer() {
        try {
            ensureSeedAccounts();

            List<Account> accounts = accountRepository.findAll();
            if (accounts.size() < MIN_ACCOUNT_COUNT) {
                return;
            }

            Account origin = chooseOriginAccount(accounts);
            if (origin == null) {
                return;
            }

            Account destination = chooseDestinationAccount(accounts, origin.getId());
            if (destination == null) {
                return;
            }

            int maxAmount = origin.getBalance()
                    .min(BigDecimal.valueOf(MAX_TRANSFER_AMOUNT))
                    .intValue();

            if (maxAmount < MIN_TRANSFER_AMOUNT) {
                return;
            }

            BigDecimal amount = BigDecimal.valueOf(random.nextInt(maxAmount) + MIN_TRANSFER_AMOUNT);
            transferService.transfer(origin.getId(), destination.getId(), amount);
            LOG.info("Mock transfer persisted: {} -> {} amount {}", origin.getId(), destination.getId(), amount);
        } catch (Exception ex) {
            LOG.warn("Failed to generate mock transfer", ex);
        }
    }

    private void ensureSeedAccounts() {
        if (accountRepository.count() >= MIN_ACCOUNT_COUNT) {
            return;
        }

        List<Account> existingAccounts = accountRepository.findAll();
        List<Account> accountsToCreate = new ArrayList<>();

        for (String name : seedNames) {
            if (existingAccounts.size() + accountsToCreate.size() >= MIN_ACCOUNT_COUNT) {
                break;
            }

            boolean alreadyExists = false;
            for (Account existing : existingAccounts) {
                if (name.equalsIgnoreCase(existing.getName())) {
                    alreadyExists = true;
                    break;
                }
            }

            if (!alreadyExists) {
                Account account = new Account();
                account.setName(name);
                account.setBalance(BigDecimal.valueOf(INITIAL_SEED_BALANCE));
                accountsToCreate.add(account);
            }
        }

        if (!accountsToCreate.isEmpty()) {
            accountRepository.saveAll(accountsToCreate);
        }
    }

    private Account chooseOriginAccount(List<Account> accounts) {
        List<Account> candidates = new ArrayList<>();
        for (Account account : accounts) {
            if (account.getBalance() != null && account.getBalance().compareTo(BigDecimal.ONE) >= 0) {
                candidates.add(account);
            }
        }

        if (candidates.isEmpty()) {
            return null;
        }

        return candidates.get(random.nextInt(candidates.size()));
    }

    private Account chooseDestinationAccount(List<Account> accounts, Long originId) {
        List<Account> candidates = new ArrayList<>();
        for (Account account : accounts) {
            if (!account.getId().equals(originId)) {
                candidates.add(account);
            }
        }

        if (candidates.isEmpty()) {
            return null;
        }

        return candidates.get(random.nextInt(candidates.size()));
    }

}

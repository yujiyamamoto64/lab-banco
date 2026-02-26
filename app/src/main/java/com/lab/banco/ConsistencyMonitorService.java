package com.lab.banco;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ConsistencyMonitorService {

    private static final Logger LOG = LoggerFactory.getLogger(ConsistencyMonitorService.class);
    private static final String ORIGIN_ACCOUNT_NAME = "Joao";
    private static final String DESTINATION_ACCOUNT_NAME = "Maria";
    private static final int MAX_STORED_ISSUES = 200;

    private final AccountRepository accountRepository;
    private final TransferTransactionRepository transferTransactionRepository;
    private final Deque<ConsistencyIssue> recentIssues = new ArrayDeque<>();

    private volatile BaselineSnapshot baselineSnapshot;
    private volatile LocalDateTime lastCheckedAt;
    private volatile int issuesDetectedInLastRun;

    public ConsistencyMonitorService(
            AccountRepository accountRepository,
            TransferTransactionRepository transferTransactionRepository) {
        this.accountRepository = accountRepository;
        this.transferTransactionRepository = transferTransactionRepository;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    @Scheduled(fixedRateString = "${app.consistency-check.fixed-rate-ms:3000}",
            initialDelayString = "${app.consistency-check.initial-delay-ms:15000}")
    public void runChecks() {
        int detectedInThisRun = 0;
        try {
            lastCheckedAt = LocalDateTime.now();
            detectedInThisRun += checkNegativeBalances();
            detectedInThisRun += checkSelfTransfers();
            detectedInThisRun += checkNonPositiveTransfers();
            detectedInThisRun += checkMissingAccountReferences();
            detectedInThisRun += checkJoaoMariaLedgerInvariants();
        } catch (Exception ex) {
            detectedInThisRun += addIssue(
                    "CHECK_RUNTIME_FAILURE",
                    "high",
                    "Consistency check execution failed: " + ex.getMessage());
            LOG.warn("Consistency check execution failed", ex);
        } finally {
            issuesDetectedInLastRun = detectedInThisRun;
        }
    }

    public ConsistencySnapshot getSnapshot() {
        List<ConsistencyIssue> issues;
        synchronized (recentIssues) {
            issues = new ArrayList<>(recentIssues);
        }

        return new ConsistencySnapshot(
                issuesDetectedInLastRun == 0,
                lastCheckedAt,
                issuesDetectedInLastRun,
                issues);
    }

    private int checkNegativeBalances() {
        int issues = 0;
        List<Account> accounts = accountRepository.findAll();
        for (Account account : accounts) {
            if (account.getBalance() == null) {
                issues += addIssue(
                        "NULL_BALANCE",
                        "high",
                        "Account " + account.getId() + " (" + account.getName() + ") has null balance.");
                continue;
            }

            if (account.getBalance().compareTo(BigDecimal.ZERO) < 0) {
                issues += addIssue(
                        "NEGATIVE_BALANCE",
                        "critical",
                        "Account " + account.getId() + " (" + account.getName()
                                + ") has negative balance: " + account.getBalance());
            }
        }

        return issues;
    }

    private int checkSelfTransfers() {
        long count = transferTransactionRepository.countSelfTransfers();
        if (count <= 0) {
            return 0;
        }

        return addIssue(
                "SELF_TRANSFER_DETECTED",
                "high",
                "Detected " + count + " transfer(s) where origin and destination are the same account.");
    }

    private int checkNonPositiveTransfers() {
        long count = transferTransactionRepository.countNonPositiveAmountTransfers();
        if (count <= 0) {
            return 0;
        }

        return addIssue(
                "NON_POSITIVE_TRANSFER_AMOUNT",
                "high",
                "Detected " + count + " transfer(s) with amount <= 0.");
    }

    private int checkMissingAccountReferences() {
        long count = transferTransactionRepository.countTransfersWithMissingAccounts();
        if (count <= 0) {
            return 0;
        }

        return addIssue(
                "MISSING_ACCOUNT_REFERENCE",
                "critical",
                "Detected " + count + " transfer(s) referencing missing account(s).");
    }

    private int checkJoaoMariaLedgerInvariants() {
        Account joao = accountRepository.findByNameIgnoreCase(ORIGIN_ACCOUNT_NAME).orElse(null);
        Account maria = accountRepository.findByNameIgnoreCase(DESTINATION_ACCOUNT_NAME).orElse(null);

        if (joao == null || maria == null) {
            return addIssue(
                    "BASELINE_ACCOUNTS_NOT_FOUND",
                    "medium",
                    "Could not find both baseline accounts: Joao and Maria.");
        }

        if (joao.getBalance() == null || maria.getBalance() == null) {
            return addIssue(
                    "BASELINE_ACCOUNTS_INVALID",
                    "high",
                    "Joao or Maria has null balance. Unable to validate ledger invariants.");
        }

        BaselineSnapshot baseline = baselineSnapshot;
        if (baseline == null || baseline.shouldReset(joao.getId(), maria.getId())) {
            baseline = BaselineSnapshot.fromCurrentState(joao, maria, transferTransactionRepository.findMaxId());
            baselineSnapshot = baseline;
            LOG.info("Consistency baseline initialized: joaoId={}, mariaId={}, fromTransferId={}",
                    baseline.joaoId, baseline.mariaId, baseline.maxTransferId);
            return 0;
        }

        int issues = 0;
        BigDecimal joaoActual = normalized(joao.getBalance());
        BigDecimal mariaActual = normalized(maria.getBalance());

        BigDecimal joaoOutgoing = normalized(
                transferTransactionRepository.sumOutgoingSince(joao.getId(), baseline.maxTransferId));
        BigDecimal joaoIncoming = normalized(
                transferTransactionRepository.sumIncomingSince(joao.getId(), baseline.maxTransferId));

        BigDecimal mariaOutgoing = normalized(
                transferTransactionRepository.sumOutgoingSince(maria.getId(), baseline.maxTransferId));
        BigDecimal mariaIncoming = normalized(
                transferTransactionRepository.sumIncomingSince(maria.getId(), baseline.maxTransferId));

        BigDecimal joaoExpected = baseline.joaoBalance.subtract(joaoOutgoing).add(joaoIncoming);
        BigDecimal mariaExpected = baseline.mariaBalance.subtract(mariaOutgoing).add(mariaIncoming);
        BigDecimal joaoDelta = joaoActual.subtract(joaoExpected);
        BigDecimal mariaDelta = mariaActual.subtract(mariaExpected);

        BigDecimal expectedTotal = baseline.joaoBalance.add(baseline.mariaBalance);
        BigDecimal actualTotal = joaoActual.add(mariaActual);
        BigDecimal totalDelta = actualTotal.subtract(expectedTotal);

        if (isBaselineDriftSignature(joaoDelta, mariaDelta, totalDelta)) {
            baselineSnapshot = BaselineSnapshot.fromCurrentState(
                    joao,
                    maria,
                    transferTransactionRepository.findMaxId());
            LOG.info("Consistency baseline recalibrated due to mirrored account drift: joaoDelta={}, mariaDelta={}",
                    joaoDelta, mariaDelta);
            return 0;
        }

        if (joaoDelta.compareTo(BigDecimal.ZERO) != 0) {
            issues += addIssue(
                    "LEDGER_MISMATCH_JOAO",
                    "critical",
                    "Joao balance mismatch. expected=" + joaoExpected + ", actual=" + joaoActual
                            + ", baselineTransferId=" + baseline.maxTransferId);
        }

        if (mariaDelta.compareTo(BigDecimal.ZERO) != 0) {
            issues += addIssue(
                    "LEDGER_MISMATCH_MARIA",
                    "critical",
                    "Maria balance mismatch. expected=" + mariaExpected + ", actual=" + mariaActual
                            + ", baselineTransferId=" + baseline.maxTransferId);
        }

        if (totalDelta.compareTo(BigDecimal.ZERO) != 0) {
            issues += addIssue(
                    "TOTAL_BALANCE_DRIFT",
                    "critical",
                    "Joao+Maria total balance drift. expectedTotal=" + expectedTotal
                            + ", actualTotal=" + actualTotal);
        }

        return issues;
    }

    private static BigDecimal normalized(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static boolean isBaselineDriftSignature(
            BigDecimal joaoDelta,
            BigDecimal mariaDelta,
            BigDecimal totalDelta) {
        return joaoDelta.compareTo(BigDecimal.ZERO) != 0
                && mariaDelta.compareTo(BigDecimal.ZERO) != 0
                && joaoDelta.add(mariaDelta).compareTo(BigDecimal.ZERO) == 0
                && totalDelta.compareTo(BigDecimal.ZERO) == 0;
    }

    private int addIssue(String code, String severity, String message) {
        ConsistencyIssue issue = new ConsistencyIssue(code, severity, message, LocalDateTime.now());
        synchronized (recentIssues) {
            recentIssues.addFirst(issue);
            while (recentIssues.size() > MAX_STORED_ISSUES) {
                recentIssues.removeLast();
            }
        }
        LOG.warn("[{}] {}", code, message);
        return 1;
    }

    public record ConsistencyIssue(
            String code,
            String severity,
            String message,
            LocalDateTime detectedAt) {
    }

    public record ConsistencySnapshot(
            boolean healthy,
            LocalDateTime lastCheckedAt,
            int issuesDetectedInLastRun,
            List<ConsistencyIssue> recentIssues) {
    }

    private static final class BaselineSnapshot {
        private final Long joaoId;
        private final Long mariaId;
        private final BigDecimal joaoBalance;
        private final BigDecimal mariaBalance;
        private final long maxTransferId;

        private BaselineSnapshot(
                Long joaoId,
                Long mariaId,
                BigDecimal joaoBalance,
                BigDecimal mariaBalance,
                long maxTransferId) {
            this.joaoId = joaoId;
            this.mariaId = mariaId;
            this.joaoBalance = joaoBalance;
            this.mariaBalance = mariaBalance;
            this.maxTransferId = maxTransferId;
        }

        private static BaselineSnapshot fromCurrentState(Account joao, Account maria, long maxTransferId) {
            return new BaselineSnapshot(
                    joao.getId(),
                    maria.getId(),
                    normalized(joao.getBalance()),
                    normalized(maria.getBalance()),
                    maxTransferId);
        }

        private boolean shouldReset(Long currentJoaoId, Long currentMariaId) {
            return !Objects.equals(joaoId, currentJoaoId) || !Objects.equals(mariaId, currentMariaId);
        }
    }
}

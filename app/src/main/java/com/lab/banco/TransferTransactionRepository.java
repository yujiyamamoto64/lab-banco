package com.lab.banco;

import java.math.BigDecimal;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TransferTransactionRepository extends JpaRepository<TransferTransaction, Long> {

    List<TransferTransaction> findTop50ByOrderByOccurredAtDesc();
    List<TransferTransaction> findTop50ByCategoryOrderByOccurredAtDesc(TransferCategory category);

    @Query("select coalesce(max(t.id), 0) from TransferTransaction t")
    long findMaxId();

    @Query("select coalesce(sum(t.amount), 0) from TransferTransaction t "
            + "where t.originAccountId = :accountId and t.id > :fromId")
    BigDecimal sumOutgoingSince(@Param("accountId") Long accountId, @Param("fromId") Long fromId);

    @Query("select coalesce(sum(t.amount), 0) from TransferTransaction t "
            + "where t.destinationAccountId = :accountId and t.id > :fromId")
    BigDecimal sumIncomingSince(@Param("accountId") Long accountId, @Param("fromId") Long fromId);

    @Query("select count(t) from TransferTransaction t where t.originAccountId = t.destinationAccountId")
    long countSelfTransfers();

    @Query("select count(t) from TransferTransaction t where t.amount <= 0")
    long countNonPositiveAmountTransfers();

    @Query(value = """
            SELECT COUNT(*)
            FROM transfer_transactions t
            LEFT JOIN accounts o ON o.id = t.origin_account_id
            LEFT JOIN accounts d ON d.id = t.destination_account_id
            WHERE o.id IS NULL OR d.id IS NULL
            """, nativeQuery = true)
    long countTransfersWithMissingAccounts();
}

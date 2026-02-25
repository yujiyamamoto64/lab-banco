package com.lab.banco;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransferTransactionRepository extends JpaRepository<TransferTransaction, Long> {

    List<TransferTransaction> findTop50ByOrderByOccurredAtDesc();
}

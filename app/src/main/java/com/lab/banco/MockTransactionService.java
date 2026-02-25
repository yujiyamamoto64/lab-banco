package com.lab.banco;

import java.time.LocalTime;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class MockTransactionService {

    private final List<String> transactions = new LinkedList<>();
    private final Random random = new Random();

    private final List<String> nomes = List.of(
            "Maria", "João", "Pedro", "Ana", "Carlos", "Bruno", "Fernanda"
    );

    @Scheduled(fixedRate = 5000)
    public void gerarTransacao() {
        String origem = nomes.get(random.nextInt(nomes.size()));
        String destino = nomes.get(random.nextInt(nomes.size()));
        int valor = random.nextInt(1000);

        String mensagem = String.format(
                "[%s] %s transferiu R$ %d para %s",
                LocalTime.now().withNano(0),
                origem,
                valor,
                destino
        );

        transactions.add(0, mensagem);

        if (transactions.size() > 50) {
            transactions.remove(transactions.size() - 1);
        }

        System.out.println(mensagem);
    }

    public List<String> getTransactions() {
        return transactions;
    }
}

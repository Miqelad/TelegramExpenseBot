package com.paata.telegram_expense_bot.service;

import com.paata.telegram_expense_bot.model.entity.Expense;
import com.paata.telegram_expense_bot.repository.ExpenseRepository;
import com.paata.telegram_expense_bot.util.VectorUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Сервис semantic search
 * по vector embeddings.
 *
 * <p>
 * Выполняет поиск расходов
 * с похожим смыслом.
 */
@Service
@RequiredArgsConstructor
public class VectorSearchService {

    private final EmbeddingService embeddingService;
    private final ExpenseRepository expenseRepository;

    /**
     * Ищет похожие расходы.
     *
     * @param text query text
     * @return similar expenses
     */
    public List<Expense> findSimilar(String text) {

        List<Float> embedding =
                embeddingService.generateEmbedding(text);

        String pgVector =
                VectorUtils.toPgVector(embedding);

        return expenseRepository
                .findSimilarExpenses(pgVector);
    }
}
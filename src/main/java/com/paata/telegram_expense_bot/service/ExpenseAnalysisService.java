package com.paata.telegram_expense_bot.service;

import com.paata.telegram_expense_bot.groq.service.GroqService;
import com.paata.telegram_expense_bot.model.entity.Expense;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;
/**
 * AI сервис анализа расходов пользователя.
 *
 * <p>
 * Использует RAG pipeline:
 * <ul>
 *     <li>semantic retrieval</li>
 *     <li>vector similarity search</li>
 *     <li>context injection</li>
 *     <li>LLM analysis</li>
 * </ul>
 *
 * <p>
 * Выполняет AI-анализ расходов пользователя
 * на основе релевантных данных.
 */
@Service
@RequiredArgsConstructor
public class ExpenseAnalysisService {

    private final VectorSearchService vectorSearchService;
    private final GroqService groqService;

    /**
     * Выполняет AI-анализ расходов пользователя.
     *
     * @param query user query
     * @return AI analysis
     */
    public String analyzeExpenses(String query) {

        List<Expense> similarExpenses =
                vectorSearchService.findSimilar(query);

        String expensesContext =
                similarExpenses.stream()
                        .map(expense ->
                                """
                                Категория: %s
                                Сумма: %s
                                Описание: %s
                                """.formatted(
                                        expense.getCategory(),
                                        expense.getAmount(),
                                        expense.getDescription()
                                )
                        )
                        .collect(Collectors.joining("\n"));

        String prompt =
                """
                Ты AI финансовый аналитик.
                
                Ниже релевантные расходы пользователя:
                
                %s
                
                Ответь на вопрос пользователя:
                
                "%s"
                """.formatted(
                        expensesContext,
                        query
                );

        return groqService.ask(prompt);
    }
}
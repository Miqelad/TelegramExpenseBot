package com.paata.telegram_expense_bot.service.ai;

import com.paata.telegram_expense_bot.groq.service.GroqService;
import com.paata.telegram_expense_bot.model.entity.Expense;
import com.paata.telegram_expense_bot.model.enums.AnalysisTopic;
import com.paata.telegram_expense_bot.prompt.PromptLoader;
import com.paata.telegram_expense_bot.service.VectorSearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Сервис AI-анализа расходов пользователя.
 *
 * <p>Использует RAG-подход: сначала ищет релевантные расходы через semantic
 * search и pgvector, затем передает найденный контекст в LLM для анализа.</p>
 */
@Service
@RequiredArgsConstructor
public class ExpenseAnalysisService {

    /**
     * Сервис semantic search по embedding-векторам расходов.
     */
    private final VectorSearchService vectorSearchService;

    /**
     * Сервис обращения к Groq LLM.
     */
    private final GroqService groqService;

    /**
     * Загрузчик prompt-файлов.
     */
    private final PromptLoader promptLoader;

    /**
     * Выполняет AI-анализ расходов по пользовательскому вопросу.
     *
     * @param analysisTopic тема анализа, распознанная LLM на этапе intent classification
     * @param query исходный вопрос пользователя
     * @return ответ финансового AI-аналитика
     */
    public String analyzeExpenses(AnalysisTopic analysisTopic, String query) {

        List<Expense> similarExpenses =
                vectorSearchService.findSimilar(query, analysisTopic);

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

        String userMessage =
                """
                Релевантные расходы пользователя:

                %s

                Вопрос пользователя:
                %s
                """.formatted(
                        expensesContext,
                        query
                );

        String template = promptLoader.loadPrompt("prompts/analyze-expenses.txt");
        return groqService.ask(template, userMessage);
    }
}

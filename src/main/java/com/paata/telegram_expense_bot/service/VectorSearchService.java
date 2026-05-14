package com.paata.telegram_expense_bot.service;

import com.paata.telegram_expense_bot.model.entity.Expense;
import com.paata.telegram_expense_bot.model.enums.AnalysisTopic;
import com.paata.telegram_expense_bot.repository.ExpenseRepository;
import com.paata.telegram_expense_bot.util.VectorUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Сервис semantic search по расходам.
 *
 * <p>Преобразует текст запроса в embedding, конвертирует его в формат pgvector
 * и ищет похожие расходы в PostgreSQL по vector similarity.</p>
 */
@Service
@RequiredArgsConstructor
public class VectorSearchService {

    /**
     * Сервис генерации embedding-векторов для пользовательского запроса.
     */
    private final EmbeddingService embeddingService;

    /**
     * Репозиторий с native SQL запросами к pgvector.
     */
    private final ExpenseRepository expenseRepository;

    /**
     * Ищет расходы, похожие по смыслу на пользовательский запрос.
     *
     * <p>Если указана тема анализа, поиск дополнительно ограничивается
     * релевантными категориями расходов.</p>
     *
     * @param text текст запроса пользователя
     * @param topic тема анализа или {@code null}, если фильтр не нужен
     * @return список наиболее похожих расходов
     */
    public List<Expense> findSimilar(String text, AnalysisTopic topic) {

        List<Float> embedding =
                embeddingService.generateEmbedding(text);

        String pgVector =
                VectorUtils.toPgVector(embedding);

        List<String> categories =
                mapTopicToCategories(topic);

        if (categories.isEmpty()) {

            return expenseRepository
                    .findSimilarExpenses(pgVector);
        }

        return expenseRepository
                .findSimilarExpensesByCategories(
                        pgVector,
                        categories
                );
    }

    /**
     * Преобразует тему AI-анализа в категории расходов для фильтрации поиска.
     *
     * @param topic тема анализа
     * @return список категорий из {@link com.paata.telegram_expense_bot.model.enums.ExpenseCategory}
     */
    private List<String> mapTopicToCategories(AnalysisTopic topic) {

        if (topic == null) {
            return List.of();
        }

        return switch (topic) {

            case HEALTH -> List.of(
                    "TOBACCO",
                    "VAPE",
                    "ALCOHOL",
                    "HEALTH",
                    "PHARMACY",
                    "FITNESS"
            );

            case SAVINGS -> List.of(
                    "RESTAURANTS",
                    "COFFEE",
                    "ALCOHOL",
                    "TAXI",
                    "SHOPPING",
                    "CLOTHES",
                    "ELECTRONICS",
                    "ENTERTAINMENT",
                    "GAMES",
                    "DIGITAL_SERVICES",
                    "SERVERS",
                    "SOFTWARE",
                    "SUBSCRIPTIONS",
                    "TOBACCO",
                    "VAPE"
            );

            case FOOD -> List.of(
                    "GROCERIES",
                    "RESTAURANTS",
                    "COFFEE",
                    "ALCOHOL"
            );

            case TRANSPORT -> List.of(
                    "TRANSPORT",
                    "TAXI",
                    "FUEL",
                    "CAR"
            );

            case SHOPPING -> List.of(
                    "SHOPPING",
                    "CLOTHES",
                    "ELECTRONICS",
                    "HOME",
                    "COSMETICS"
            );

            default -> List.of();
        };
    }
}

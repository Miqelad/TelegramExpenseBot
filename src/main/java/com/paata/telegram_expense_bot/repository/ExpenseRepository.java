package com.paata.telegram_expense_bot.repository;

import com.paata.telegram_expense_bot.model.entity.Expense;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Репозиторий расходов.
 *
 * <p>Расходы сохраняются с Telegram user id и username автора, но методы отчетов
 * и semantic search читают общую базу без фильтрации по пользователю.</p>
 */
public interface ExpenseRepository extends JpaRepository<Expense, UUID> {

    /**
     * Возвращает все расходы за указанный период.
     *
     * @param start начало периода включительно
     * @param end конец периода включительно
     * @return список расходов за период
     */
    List<Expense> findAllByCreatedAtBetween(
            LocalDateTime start,
            LocalDateTime end
    );

    /**
     * Обновляет embedding расхода после сохранения основной записи.
     *
     * @param uuid идентификатор расхода
     * @param embedding строка в формате pgvector, например {@code [0.1,0.2]}
     */
    @Modifying
    @Transactional
    @Query(value = """
            UPDATE expenses
            SET embedding = CAST(:embedding AS vector)
            WHERE uuid = :uuid
            """, nativeQuery = true)
    void updateEmbedding(
            @Param("uuid") UUID uuid,
            @Param("embedding") String embedding
    );

    /**
     * Выполняет semantic search по всем расходам.
     *
     * @param embedding embedding пользовательского запроса в формате pgvector
     * @return до пяти наиболее похожих расходов
     */
    @Query(value = """
        SELECT *
        FROM expenses
        WHERE embedding IS NOT NULL
        ORDER BY embedding <-> CAST(:embedding AS vector)
        LIMIT 5
        """, nativeQuery = true)
    List<Expense> findSimilarExpenses(@Param("embedding") String embedding);

    /**
     * Выполняет semantic search с фильтром по категориям.
     *
     * @param embedding embedding пользовательского запроса в формате pgvector
     * @param categories категории расходов, которые нужно учитывать
     * @return до пяти наиболее похожих расходов внутри указанных категорий
     */
    @Query(value = """
        SELECT *
        FROM expenses
        WHERE embedding IS NOT NULL
        AND category IN (:categories)
        ORDER BY embedding <-> CAST(:embedding AS vector)
        LIMIT 5
        """, nativeQuery = true)
    List<Expense> findSimilarExpensesByCategories(
            @Param("embedding") String embedding,
            @Param("categories") List<String> categories
    );
}

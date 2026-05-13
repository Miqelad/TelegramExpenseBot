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
 * <p>Помимо стандартных JPA-операций содержит native SQL запросы для обновления
 * embedding-поля и поиска похожих расходов через pgvector.</p>
 */
public interface ExpenseRepository extends JpaRepository<Expense, UUID> {

    /**
     * Возвращает все расходы пользователя.
     *
     * @param userId Telegram user id пользователя
     * @return список всех расходов пользователя
     */
    List<Expense> findAllByUserId(Long userId);

    /**
     * Возвращает расходы пользователя в указанном диапазоне дат.
     *
     * @param userId Telegram user id пользователя
     * @param start начало периода включительно
     * @param end конец периода включительно
     * @return список расходов за период
     */
    List<Expense> findAllByUserIdAndCreatedAtBetween(
            Long userId,
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
        WHERE category IN (:categories)
        ORDER BY embedding <-> CAST(:embedding AS vector)
        LIMIT 5
        """, nativeQuery = true)
    List<Expense> findSimilarExpensesByCategories(
            @Param("embedding") String embedding,
            @Param("categories") List<String> categories
    );
}

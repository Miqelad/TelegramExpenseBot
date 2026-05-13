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

public interface ExpenseRepository extends JpaRepository<Expense, UUID> {

    List<Expense> findAllByUserId(Long userId);

    /**
     * Возвращает список расходов пользователя
     * в указанном диапазоне дат.
     *
     * @param userId идентификатор пользователя Telegram
     * @param start  дата начала периода
     * @param end    дата конца периода
     * @return список расходов
     */
    List<Expense> findAllByUserIdAndCreatedAtBetween(
            Long userId,
            LocalDateTime start,
            LocalDateTime end
    );

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
     * Выполняет semantic search
     * похожих расходов.
     *
     * @param embedding query embedding
     * @return похожие расходы
     */
    @Query(value = """
        SELECT *
        FROM expenses
        ORDER BY embedding <-> CAST(:embedding AS vector)
        LIMIT 5
        """, nativeQuery = true)
    List<Expense> findSimilarExpenses(@Param("embedding") String embedding);
}
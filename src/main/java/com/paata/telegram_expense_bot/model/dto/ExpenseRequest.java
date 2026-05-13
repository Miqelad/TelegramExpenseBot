package com.paata.telegram_expense_bot.model.dto;

import com.paata.telegram_expense_bot.model.enums.ExpenseCategory;
import lombok.Data;

import java.math.BigDecimal;

/**
 * DTO одного расхода, извлеченного LLM из сообщения пользователя.
 *
 * <p>Используется как промежуточная структура между JSON-ответом модели
 * и entity {@code Expense}.</p>
 */
@Data
public class ExpenseRequest {

    /**
     * Сумма расхода.
     */
    private BigDecimal amount;

    /**
     * Нормализованная категория расхода.
     */
    private ExpenseCategory category;

    /**
     * Короткое описание расхода из исходного сообщения.
     */
    private String description;
}

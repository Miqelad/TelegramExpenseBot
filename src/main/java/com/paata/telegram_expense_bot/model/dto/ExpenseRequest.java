package com.paata.telegram_expense_bot.model.dto;

import com.paata.telegram_expense_bot.model.enums.ExpenseCategory;
import lombok.Data;

import java.math.BigDecimal;

/**
 * DTO расхода.
 */
@Data
public class ExpenseRequest {

    /**
     * Сумма расхода.
     */
    private BigDecimal amount;

    /**
     * Категория.
     */
    private ExpenseCategory category;

    /**
     * Описание.
     */
    private String description;
}
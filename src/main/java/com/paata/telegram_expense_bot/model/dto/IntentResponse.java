package com.paata.telegram_expense_bot.model.dto;

import com.paata.telegram_expense_bot.model.enums.IntentType;
import lombok.Data;

/**
 * DTO ответа AI intent classifier.
 */
@Data
public class IntentResponse {

    /**
     * Определенный intent пользователя.
     */
    private IntentType intent;
}
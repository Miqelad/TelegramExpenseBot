package com.paata.telegram_expense_bot.model.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * DTO вычисленного периода отчета.
 *
 * <p>Используется после разбора пользовательского запроса, когда абстрактные
 * параметры вроде "за апрель" уже превращены в конкретные даты.</p>
 */
@Getter
@AllArgsConstructor
public class ReportPeriod {

    /**
     * Дата и время начала периода.
     */
    private LocalDateTime startDate;

    /**
     * Дата и время конца периода.
     */
    private LocalDateTime endDate;
}

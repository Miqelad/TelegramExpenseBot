package com.paata.telegram_expense_bot.service.report;

import com.paata.telegram_expense_bot.model.dto.ReportPeriod;
import com.paata.telegram_expense_bot.model.dto.ReportRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * Сервис вычисления периода отчета.
 *
 * <p>Получает структурированный {@link ReportRequest}, который был распознан
 * LLM, и превращает его в конкретные даты начала и конца периода.</p>
 */
@Service
public class ReportPeriodService {

    /**
     * Часовой пояс приложения для расчета относительных периодов отчетов.
     */
    @Value("${app.time-zone}")
    private String appTimeZone;

    /**
     * Вычисляет период отчета.
     *
     * <p>Поддерживаются явные даты, количество последних месяцев,
     * конкретный месяц и значение по умолчанию - текущий месяц.</p>
     *
     * @param reportRequest параметры отчета после AI-парсинга
     * @return период с датой начала и датой конца
     */
    public ReportPeriod resolvePeriod(
            ReportRequest reportRequest
    ) {

        ZoneId zoneId =
                ZoneId.of(appTimeZone);

        LocalDate today =
                LocalDate.now(zoneId);

        LocalDateTime startDate;

        LocalDateTime endDate =
                LocalDateTime.now(zoneId);

        if (reportRequest.getDateFrom() != null) {

            startDate =
                    LocalDate.parse(
                            reportRequest.getDateFrom()
                    ).atStartOfDay();

            if (reportRequest.getDateTo() != null) {

                endDate =
                        LocalDate.parse(
                                reportRequest.getDateTo()
                        ).atTime(23, 59);
            }

        } else if (reportRequest.getMonths() != null) {

            startDate =
                    endDate.minusMonths(
                            reportRequest.getMonths()
                    );

        } else if (reportRequest.getMonth() != null) {

            int month =
                    parseMonth(
                            reportRequest.getMonth()
                    );

            startDate =
                    today
                            .withMonth(month)
                            .withDayOfMonth(1)
                            .atStartOfDay();

            endDate =
                    startDate
                            .plusMonths(1)
                            .minusSeconds(1);

        } else {

            startDate =
                    today
                            .withDayOfMonth(1)
                            .atStartOfDay();
        }

        return new ReportPeriod(
                startDate,
                endDate
        );
    }

    /**
     * Конвертирует английское название месяца в номер месяца.
     *
     * @param month название месяца, например {@code APRIL}
     * @return номер месяца от 1 до 12; если значение неизвестно, текущий месяц
     */
    private int parseMonth(String month) {

        return switch (month.toUpperCase()) {

            case "JANUARY" -> 1;
            case "FEBRUARY" -> 2;
            case "MARCH" -> 3;
            case "APRIL" -> 4;
            case "MAY" -> 5;
            case "JUNE" -> 6;
            case "JULY" -> 7;
            case "AUGUST" -> 8;
            case "SEPTEMBER" -> 9;
            case "OCTOBER" -> 10;
            case "NOVEMBER" -> 11;
            case "DECEMBER" -> 12;

            default -> LocalDate.now(ZoneId.of(appTimeZone)).getMonthValue();
        };
    }
}

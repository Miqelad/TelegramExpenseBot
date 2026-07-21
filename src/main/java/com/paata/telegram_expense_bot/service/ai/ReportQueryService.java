package com.paata.telegram_expense_bot.service.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paata.telegram_expense_bot.gemini.service.GeminiService;
import com.paata.telegram_expense_bot.model.dto.ReportRequest;
import com.paata.telegram_expense_bot.prompt.PromptLoader;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Locale;

/**
 * AI-сервис разбора пользовательского запроса на отчет.
 *
 * <p>Преобразует фразы вроде "отчет за апрель", "за последние 3 месяца"
 * или "с 2026-01-01 по 2026-02-01" в DTO {@link ReportRequest}.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReportQueryService {

    /**
     * Сервис обращения к Gemini LLM.
     */
    private final GeminiService geminiService;

    /**
     * JSON-маппер для разбора структурированного ответа LLM.
     */
    private final ObjectMapper objectMapper =
            new ObjectMapper();

    /**
     * Загрузчик prompt-файлов.
     */
    private final PromptLoader promptLoader;

    /**
     * Извлекает параметры отчета из текста пользователя.
     *
     * @param text исходный пользовательский запрос
     * @return структурированные параметры периода отчета
     */
    public ReportRequest parseReportQuery(String text) {
        try {
            if (isCurrentMonthRequest(text)) {
                return new ReportRequest(
                        null,
                        null,
                        null,
                        null
                );
            }

            String template = promptLoader.loadPrompt("prompts/report-parser.txt");
            String response =
                    geminiService.ask(template, text);
            log.info("LLM parseReportQuery response: {}", response);
            String cleanResponse = promptLoader.clean(response);
            return objectMapper.readValue(
                    cleanResponse,
                    ReportRequest.class
            );
        } catch (Exception e) {
            throw new RuntimeException(
                    "Ошибка парсинга запроса отчета",
                    e
            );
        }
    }

    /**
     * Проверяет, просит ли пользователь отчет за текущий месяц.
     *
     * <p>Такие относительные фразы не отправляются в LLM, потому что модель может
     * ошибочно выбрать конкретный месяц. Пустой {@link ReportRequest} дальше
     * трактуется как текущий месяц в ReportPeriodService.</p>
     *
     * @param text исходный пользовательский текст
     * @return {@code true}, если запрос явно относится к текущему месяцу
     */
    private boolean isCurrentMonthRequest(String text) {
        String normalizedText =
                text.toLowerCase(Locale.ROOT);

        return normalizedText.contains("этот месяц")
                || normalizedText.contains("текущий месяц")
                || normalizedText.contains("нынешний месяц")
                || normalizedText.contains("этом месяце")
                || normalizedText.contains("текущем месяце")
                || normalizedText.matches(".*\\bза\\s+месяц\\b.*");
    }
}

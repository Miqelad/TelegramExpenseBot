package com.paata.telegram_expense_bot.service.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paata.telegram_expense_bot.gemini.service.GeminiService;
import com.paata.telegram_expense_bot.model.dto.CategoryReportRequest;
import com.paata.telegram_expense_bot.prompt.PromptLoader;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Locale;

/**
 * AI-сервис разбора пользовательского запроса на отчет по категории.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CategoryReportQueryService {

    /**
     * Сервис обращения к Gemini LLM.
     */
    private final GeminiService geminiService;

    /**
     * Загрузчик prompt-файлов.
     */
    private final PromptLoader promptLoader;

    /**
     * JSON-маппер для разбора структурированного ответа LLM.
     */
    private final ObjectMapper objectMapper =
            new ObjectMapper();

    /**
     * Извлекает категорию, поисковый текст и период из запроса пользователя.
     *
     * @param text исходный пользовательский запрос
     * @return структурированные параметры отчета по категории
     */
    public CategoryReportRequest parseCategoryReportQuery(String text) {
        try {
            String template =
                    promptLoader.loadPrompt(
                            "prompts/category-report-parser.txt"
                    );
            String response =
                    geminiService.ask(
                            template,
                            text
                    );
            log.info("LLM parseCategoryReportQuery response: {}", response);
            String cleanResponse =
                    promptLoader.clean(response);
            CategoryReportRequest reportRequest =
                    objectMapper.readValue(
                    cleanResponse,
                    CategoryReportRequest.class
            );
            resetPeriodForCurrentMonthRequest(
                    text,
                    reportRequest
            );

            return reportRequest;
        } catch (Exception e) {
            throw new RuntimeException(
                    "Ошибка парсинга запроса отчета по категории",
                    e
            );
        }
    }

    /**
     * Сбрасывает период в текущий месяц, если пользователь явно написал
     * относительную фразу вроде "этот месяц".
     *
     * <p>Категорию и поисковый текст оставляем из ответа LLM, а поля периода
     * очищаем. Дальше ReportPeriodService интерпретирует пустой период как
     * текущий месяц в часовом поясе приложения.</p>
     *
     * @param text исходный пользовательский текст
     * @param reportRequest разобранный запрос отчета по категории
     */
    private void resetPeriodForCurrentMonthRequest(
            String text,
            CategoryReportRequest reportRequest
    ) {
        String normalizedText =
                text.toLowerCase(Locale.ROOT);

        if (normalizedText.contains("этот месяц")
                || normalizedText.contains("текущий месяц")
                || normalizedText.contains("нынешний месяц")
                || normalizedText.contains("этом месяце")
                || normalizedText.contains("текущем месяце")
                || normalizedText.matches(".*\\bза\\s+месяц\\b.*")) {
            reportRequest.setMonths(null);
            reportRequest.setMonth(null);
            reportRequest.setDateFrom(null);
            reportRequest.setDateTo(null);
        }
    }
}

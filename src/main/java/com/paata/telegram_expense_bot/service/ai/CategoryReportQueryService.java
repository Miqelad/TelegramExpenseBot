package com.paata.telegram_expense_bot.service.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paata.telegram_expense_bot.groq.service.GroqService;
import com.paata.telegram_expense_bot.model.dto.CategoryReportRequest;
import com.paata.telegram_expense_bot.prompt.PromptLoader;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * AI-сервис разбора пользовательского запроса на отчет по категории.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CategoryReportQueryService {

    /**
     * Сервис обращения к Groq LLM.
     */
    private final GroqService groqService;

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
                    groqService.ask(
                            template,
                            text
                    );
            log.info("LLM parseCategoryReportQuery response: {}", response);
            String cleanResponse =
                    promptLoader.clean(response);
            return objectMapper.readValue(
                    cleanResponse,
                    CategoryReportRequest.class
            );
        } catch (Exception e) {
            throw new RuntimeException(
                    "Ошибка парсинга запроса отчета по категории",
                    e
            );
        }
    }
}

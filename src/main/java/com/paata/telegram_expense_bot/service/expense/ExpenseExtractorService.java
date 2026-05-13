package com.paata.telegram_expense_bot.service.expense;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.paata.telegram_expense_bot.groq.service.GroqService;
import com.paata.telegram_expense_bot.model.dto.ExpenseRequest;
import com.paata.telegram_expense_bot.model.entity.Expense;
import com.paata.telegram_expense_bot.prompt.PromptLoader;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Сервис извлечения расходов из свободного текста.
 *
 * <p>Использует LLM и prompt {@code expense-extractor.txt}, чтобы превратить
 * пользовательское сообщение в список структурированных расходов. Telegram user id
 * и username добавляются в каждую созданную entity как информация об авторе.</p>
 */
@Service
@RequiredArgsConstructor
public class ExpenseExtractorService {

    /**
     * Сервис обращения к Groq LLM.
     */
    private final GroqService groqService;

    /**
     * Загрузчик prompt-файлов из classpath.
     */
    private final PromptLoader promptLoader;

    /**
     * JSON-маппер для разбора ответа LLM в DTO.
     */
    private final ObjectMapper objectMapper =
            new ObjectMapper();

    /**
     * Извлекает расходы из сообщения пользователя.
     *
     * @param userId Telegram user id автора расходов
     * @param username Telegram username автора расходов
     * @param text исходное сообщение пользователя
     * @return список распознанных расходов
     */
    public List<Expense> extractExpenses(
            Long userId,
            String username,
            String text
    ) {

        try {

            String systemPrompt =
                    promptLoader.loadPrompt(
                            "prompts/expense-extractor.txt"
                    );

            String response =
                    groqService.ask(
                            systemPrompt,
                            text
                    );

            List<ExpenseRequest> dtos =
                    objectMapper.readValue(
                            response,
                            new TypeReference<List<ExpenseRequest>>() {}
                    );

            return dtos.stream()
                    .map(dto ->
                            Expense.builder()
                                    .userId(userId)
                                    .username(username)
                                    .amount(dto.getAmount())
                                    .category(dto.getCategory())
                                    .description(dto.getDescription())
                                    .createdAt(LocalDateTime.now())
                                    .build()
                    )
                    .toList();

        } catch (Exception e) {

            throw new RuntimeException(
                    "Ошибка AI-извлечения расходов",
                    e
            );
        }
    }
}

package com.paata.telegram_expense_bot.groq.service;

import com.paata.telegram_expense_bot.model.dto.IntentResponse;
import com.paata.telegram_expense_bot.model.enums.IntentType;
import com.paata.telegram_expense_bot.prompt.PromptLoader;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

/**
 * Сервис для работы с Groq Chat Completions API.
 *
 * <p>Отвечает за классификацию намерений пользователя и за произвольные
 * LLM-запросы, где вызывающий код передает system prompt и сообщение
 * пользователя.</p>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class GroqService {

    private final PromptLoader promptLoader;

    /**
     * API-ключ Groq. Значение приходит из переменной окружения {@code GROQ_API_KEY}.
     */
    @Value("${groq.api-key}")
    private String apiKey;

    /**
     * JSON-маппер для разбора структурированных ответов LLM.
     */
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * HTTP-клиент для вызова внешнего Groq API.
     */
    private final WebClient webClient;

    /**
     * Определяет намерение пользователя по тексту сообщения.
     *
     * <p>LLM должна вернуть JSON, который десериализуется в {@link IntentResponse}.
     * Если Groq недоступен, вернул ошибку или невалидный JSON, метод возвращает
     * безопасное значение {@link IntentType#UNKNOWN}, чтобы бот не падал.</p>
     *
     * @param text исходное сообщение пользователя из Telegram
     * @return распознанное намерение и, если есть, тема анализа
     */
    public IntentResponse detectIntent(String text) {

        try {

            String systemPrompt =
                    promptLoader.loadPrompt(
                            "prompts/intent-classifier.txt"
                    );

            Map<String, Object> requestBody = Map.of(
                    "model", "llama-3.3-70b-versatile",
                    "messages", List.of(
                            Map.of(
                                    "role", "system",
                                    "content", systemPrompt
                            ),
                            Map.of(
                                    "role", "user",
                                    "content", text
                            )
                    )
            );

            Map response = webClient.post()
                    .uri("https://api.groq.com/openai/v1/chat/completions")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .header(HttpHeaders.CONTENT_TYPE, "application/json")
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            List choices =
                    (List) response.get("choices");

            Map firstChoice =
                    (Map) choices.getFirst();

            Map message =
                    (Map) firstChoice.get("message");

            String content =
                    (String) message.get("content");

            IntentResponse intentResponse = objectMapper.readValue(
                    content,
                    IntentResponse.class
            );

            if (intentResponse.getIntent() == null) {
                log.warn("Groq returned empty intent. Raw content: {}", content);
                return unknownIntent();
            }

            return intentResponse;

        } catch (Exception e) {

            log.error("Failed to detect intent with Groq", e);
            return unknownIntent();
        }
    }

    /**
     * Создает fallback-ответ для случаев, когда intent распознать не удалось.
     *
     * @return ответ с intent {@link IntentType#UNKNOWN}
     */
    private IntentResponse unknownIntent() {
        IntentResponse intentResponse = new IntentResponse();
        intentResponse.setIntent(IntentType.UNKNOWN);
        return intentResponse;
    }

    /**
     * Отправляет произвольный запрос в LLM.
     *
     * @param systemPrompt системная инструкция для модели
     * @param userMessage сообщение пользователя или подготовленный запрос
     * @return текстовый ответ модели или описание ошибки Groq API
     */
    public String ask(String systemPrompt, String userMessage) {
        try {
            Map<String, Object> requestBody = Map.of(
                    "model", "llama-3.3-70b-versatile",
                    "messages", List.of(
                            Map.of(
                                    "role", "system",
                                    "content", systemPrompt
                            ),
                            Map.of(
                                    "role", "user",
                                    "content", userMessage
                            )
                    )
            );
            Map response = webClient.post()
                    .uri("https://api.groq.com/openai/v1/chat/completions")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .header(HttpHeaders.CONTENT_TYPE, "application/json")
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();
            List choices = (List) response.get("choices");
            Map firstChoice = (Map) choices.getFirst();
            Map message = (Map) firstChoice.get("message");
            return (String) message.get("content");
        } catch (Exception e) {
            e.printStackTrace();
            return "Groq API error: " + e.getMessage();
        }
    }
}

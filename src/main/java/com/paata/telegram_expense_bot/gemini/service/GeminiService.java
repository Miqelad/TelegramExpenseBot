package com.paata.telegram_expense_bot.gemini.service;

import com.paata.telegram_expense_bot.model.dto.IntentResponse;
import com.paata.telegram_expense_bot.model.enums.IntentType;
import com.paata.telegram_expense_bot.prompt.PromptLoader;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

/**
 * Сервис для работы с Gemini Generate Content API.
 *
 * <p>Отвечает за классификацию намерений пользователя и за произвольные
 * LLM-запросы, где вызывающий код передает system prompt и пользовательское
 * сообщение. API-ключ и список моделей берутся из настроек {@code gemini.api-key}
 * и {@code gemini.chat-models}.</p>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class GeminiService {

    private static final String GEMINI_API_BASE_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/";

    private final PromptLoader promptLoader;
    private final WebClient webClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${gemini.api-key}")
    private String apiKey;

    @Value("${gemini.chat-models}")
    private List<String> chatModels;

    /**
     * Определяет намерение пользователя по тексту Telegram-сообщения.
     *
     * <p>Метод загружает prompt {@code prompts/intent-classifier.txt}, отправляет
     * его в Gemini вместе с текстом пользователя и разбирает JSON-ответ в
     * {@link IntentResponse}. Если Gemini недоступен, вернул пустой intent или
     * ответ нельзя разобрать как JSON, возвращается безопасный
     * {@link IntentType#UNKNOWN}.</p>
     *
     * @param text исходный текст сообщения пользователя
     * @return распознанное намерение и дополнительные параметры анализа
     */
    public IntentResponse detectIntent(String text) {
        try {
            String systemPrompt =
                    promptLoader.loadPrompt(
                            "prompts/intent-classifier.txt"
                    );

            String content =
                    ask(systemPrompt, text);

            if (content.startsWith("Gemini API error:")) {
                return unknownIntent();
            }

            IntentResponse intentResponse = objectMapper.readValue(
                    extractJson(content),
                    IntentResponse.class
            );

            if (intentResponse.getIntent() == null) {
                log.warn("Gemini returned empty intent. Raw content: {}", content);
                return unknownIntent();
            }

            return intentResponse;
        } catch (Exception e) {
            log.error("Failed to detect intent with Gemini", e);
            return unknownIntent();
        }
    }

    /**
     * Отправляет произвольный текстовый запрос в Gemini.
     *
     * <p>System prompt передается через {@code systemInstruction}, а сообщение
     * пользователя - через {@code contents}. Метод перебирает модели из настройки
     * {@code gemini.chat-models}: если одна модель недоступна и Gemini вернул
     * {@code 404 Not Found}, пробуется следующая. При другой ошибке API
     * возвращается текст с описанием ошибки, чтобы вызывающий код мог показать
     * пользователю понятный fallback.</p>
     *
     * @param systemPrompt системная инструкция для модели
     * @param userMessage пользовательское сообщение или подготовленный запрос
     * @return текст ответа Gemini или строка с описанием ошибки API
     */
    public String ask(String systemPrompt, String userMessage) {
        for (String model : chatModels) {
            try {
                return requestGenerateContent(
                        model.trim(),
                        systemPrompt,
                        userMessage
                );
            } catch (WebClientResponseException.NotFound e) {
                log.warn("Gemini chat model {} is not available, trying next model", model);
            } catch (Exception e) {
                log.error("Gemini API request failed", e);
                return "Gemini API error: " + e.getMessage();
            }
        }

        return "Gemini API error: no configured chat model is available";
    }

    /**
     * Выполняет один запрос к Gemini {@code generateContent}.
     *
     * @param model модель Gemini, которую нужно вызвать
     * @param systemPrompt системная инструкция для модели
     * @param userMessage пользовательское сообщение или подготовленный запрос
     * @return текст первого фрагмента ответа Gemini
     */
    private String requestGenerateContent(
            String model,
            String systemPrompt,
            String userMessage
    ) {
        try {
            Map<String, Object> requestBody = Map.of(
                    "systemInstruction", Map.of(
                            "parts", List.of(
                                    Map.of("text", systemPrompt)
                            )
                    ),
                    "contents", List.of(
                            Map.of(
                                    "role", "user",
                                    "parts", List.of(
                                            Map.of("text", userMessage)
                                    )
                            )
                    )
            );

            Map response =
                    webClient.post()
                            .uri(GEMINI_API_BASE_URL + model + ":generateContent")
                            .header("x-goog-api-key", apiKey)
                            .header(HttpHeaders.CONTENT_TYPE, "application/json")
                            .bodyValue(requestBody)
                            .retrieve()
                            .bodyToMono(Map.class)
                            .block();

            return extractText(response);
        } catch (WebClientResponseException.NotFound e) {
            throw e;
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
     * Достает текст ответа из структуры Gemini {@code candidates[].content.parts[]}.
     *
     * @param response ответ Gemini API, десериализованный в {@link Map}
     * @return текст первого фрагмента ответа
     */
    private String extractText(Map response) {
        List candidates =
                (List) response.get("candidates");

        Map firstCandidate =
                (Map) candidates.getFirst();

        Map content =
                (Map) firstCandidate.get("content");

        List parts =
                (List) content.get("parts");

        Map firstPart =
                (Map) parts.getFirst();

        return (String) firstPart.get("text");
    }

    /**
     * Извлекает JSON из ответа модели.
     *
     * <p>Gemini иногда возвращает JSON внутри markdown-блока
     * {@code ```json ... ```}. Метод сначала снимает такой блок, затем пытается
     * выделить JSON-объект по фигурным скобкам. Это делает парсинг intent более
     * устойчивым к форматированию ответа модели.</p>
     *
     * @param content сырой текст ответа Gemini
     * @return строка, которую можно передать в JSON parser
     */
    private String extractJson(String content) {
        String trimmed = content.trim();
        if (trimmed.startsWith("```")) {
            int firstLineEnd = trimmed.indexOf('\n');
            int closingFence = trimmed.lastIndexOf("```");
            if (firstLineEnd > 0 && closingFence > firstLineEnd) {
                return trimmed.substring(firstLineEnd + 1, closingFence).trim();
            }
        }

        int objectStart = trimmed.indexOf('{');
        int objectEnd = trimmed.lastIndexOf('}');
        if (objectStart >= 0 && objectEnd > objectStart) {
            return trimmed.substring(objectStart, objectEnd + 1);
        }

        return trimmed;
    }
}

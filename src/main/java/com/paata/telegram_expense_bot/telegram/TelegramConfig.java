package com.paata.telegram_expense_bot.telegram;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.meta.generics.TelegramClient;

/**
 * Конфигурация клиента Telegram Bot API.
 *
 * <p>Создает {@link TelegramClient}, через который приложение отправляет
 * сообщения пользователям в Telegram.</p>
 */
@Configuration
public class TelegramConfig {

    /**
     * Создает клиент Telegram на основе токена бота.
     *
     * @param botToken токен Telegram-бота из конфигурации
     * @return клиент для вызовов Telegram Bot API
     */
    @Bean
    public TelegramClient telegramClient(@Value("${telegram.bot.token}") String botToken) {
        return new OkHttpTelegramClient(botToken);
    }
}

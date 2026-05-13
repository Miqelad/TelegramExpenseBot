package com.paata.telegram_expense_bot.telegram;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.meta.generics.TelegramClient;

/**
 * Конфигурация Telegram клиента.
 *
 * <p>
 * Здесь создаются Spring Beans
 * для работы с Telegram API.
 */
@Configuration
public class TelegramConfig {

    /**
     * Создает TelegramClient bean.
     *
     * @param botToken token Telegram бота
     * @return telegram client
     */
    @Bean
    public TelegramClient telegramClient(@Value("${telegram.bot.token}") String botToken) {
        return new OkHttpTelegramClient(botToken);
    }
}
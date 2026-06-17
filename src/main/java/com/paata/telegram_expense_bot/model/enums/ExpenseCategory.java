package com.paata.telegram_expense_bot.model.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Категории расходов, в которые LLM нормализует пользовательские сообщения.
 */
@Getter
@RequiredArgsConstructor
public enum ExpenseCategory {

    // Еда
    GROCERIES("Продукты"),
    RESTAURANTS("Рестораны"),
    COFFEE("Кофе"),
    ALCOHOL("Алкоголь"),

    // Транспорт
    TRANSPORT("Транспорт"),
    TAXI("Такси"),
    FUEL("Топливо"),
    CAR("Автомобиль"),

    // Дом
    RENT("Аренда"),
    UTILITIES("Коммунальные услуги"),
    HOME("Дом"),

    // Здоровье
    HEALTH("Здоровье"),
    PHARMACY("Аптека"),
    FITNESS("Фитнес"),

    // Уход
    SELFCARE("Уход за собой"),
    COSMETICS("Косметика"),

    // Животные
    PET("Питомцы"),

    // Социальное
    FRIENDS("Друзья"),
    GIFTS("Подарки"),
    DATES("Свидания"),

    // Развлечения
    ENTERTAINMENT("Развлечения"),
    GAMES("Игры"),
    TRAVEL("Путешествия"),

    // Покупки
    SHOPPING("Покупки"),
    CLOTHES("Одежда"),
    ELECTRONICS("Электроника"),

    // Digital / IT
    DIGITAL_SERVICES("Цифровые сервисы"),
    SERVERS("Серверы"),
    SOFTWARE("ПО"),
    SUBSCRIPTIONS("Подписки"),
    EDUCATION("Обучение"),
    WORK_EXPENSES("Рабочие расходы"),

    // Финансы
    CREDIT_PAYMENT("Платежи по кредиту"),
    TAXES("Налоги"),
    INSURANCE("Страхование"),

    // Привычки
    TOBACCO("Табак"),
    VAPE("Вейп"),

    OTHER("Прочее");

    private final String displayName;
}
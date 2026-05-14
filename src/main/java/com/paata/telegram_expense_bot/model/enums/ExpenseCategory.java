package com.paata.telegram_expense_bot.model.enums;

/**
 * Категории расходов, в которые LLM нормализует пользовательские сообщения.
 */
public enum ExpenseCategory {

    // Еда
    GROCERIES,
    RESTAURANTS,
    COFFEE,
    ALCOHOL,

    // Транспорт
    TRANSPORT,
    TAXI,
    FUEL,
    CAR,

    // Дом
    RENT,
    UTILITIES,
    HOME,

    // Здоровье
    HEALTH,
    PHARMACY,
    FITNESS,

    // Уход
    SELFCARE,
    COSMETICS,

    // Животные
    PET,

    // Социальное
    FRIENDS,
    GIFTS,
    DATES,

    // Развлечения
    ENTERTAINMENT,
    GAMES,
    TRAVEL,

    // Покупки
    SHOPPING,
    CLOTHES,
    ELECTRONICS,

    // Digital / IT
    DIGITAL_SERVICES,
    SERVERS,
    SOFTWARE,
    SUBSCRIPTIONS,
    EDUCATION,
    WORK_EXPENSES,

    // Финансы
    CREDIT_PAYMENT,
    TAXES,
    INSURANCE,

    // Привычки
    TOBACCO,
    VAPE,

    OTHER
}

package com.paata.telegram_expense_bot.model.entity;

import com.paata.telegram_expense_bot.model.enums.ExpenseCategory;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Entity расхода.
 *
 * <p>Хранится в таблице {@code expenses}. Расход сохраняет Telegram user id
 * и username автора записи, но отчеты и AI-анализ читают общую базу расходов
 * без фильтрации по пользователю.</p>
 */
@Entity
@Table(name = "expenses")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class Expense {

    /**
     * Уникальный идентификатор расхода.
     */
    @Id
    @UuidGenerator
    @Column(nullable = false, updatable = false)
    private UUID uuid;

    /**
     * Telegram user id автора расхода.
     */
    @Column(name = "user_id")
    private Long userId;

    /**
     * Telegram username автора расхода.
     */
    @Column(name = "username")
    private String username;

    /**
     * Сумма расхода.
     */
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    /**
     * Нормализованная категория расхода.
     */
    @Enumerated(EnumType.STRING)
    private ExpenseCategory category;

    /**
     * Текстовое описание расхода.
     */
    private String description;

    /**
     * Дата и время создания записи.
     */
    @Column(name = "created_at")
    private LocalDateTime createdAt;
}

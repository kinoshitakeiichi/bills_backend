package com.jiajunxie.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;
import lombok.Data;

@Data
@Entity
@TableName("bills.b_reminders")
@Table(
    name = "b_reminders",
    schema = "bills",
    indexes = {
      @Index(name = "idx_reminders_enabled", columnList = "enabled"),
      @Index(name = "idx_reminders_account", columnList = "account_id")
    })
public class Reminder {

  @Id
  @TableId(value = "reminder_id", type = IdType.INPUT)
  @Column(name = "reminder_id", length = 36, nullable = false)
  private String reminderId;

  @Column(name = "reminder_name", length = 100, nullable = false)
  private String reminderName;

  @Column(name = "reminder_type_code", length = 50, nullable = false)
  private String reminderTypeCode;

  @Column(name = "account_id", length = 36)
  private String accountId;

  @Column(name = "schedule_mode_code", length = 30, nullable = false)
  private String scheduleModeCode;

  @Column(name = "remind_date")
  private LocalDate remindDate;

  @Column(name = "day_of_month")
  private Integer dayOfMonth;

  @Column(name = "remind_time", nullable = false)
  private LocalTime remindTime;

  @Column(name = "timezone", length = 100, nullable = false)
  private String timezone;

  @Column(name = "date_rule_source_code", length = 30, nullable = false)
  private String dateRuleSourceCode = "CUSTOM";

  @Column(name = "invalid_day_rule_code", length = 30, nullable = false)
  private String invalidDayRuleCode = "LAST_DAY";

  @Column(name = "calendar_code", length = 50)
  private String calendarCode;

  @Column(name = "business_day_adjustment_code", length = 30, nullable = false)
  private String businessDayAdjustmentCode = "NONE";

  @Column(name = "notification_url", length = 1000)
  private String notificationUrl;

  @Column(name = "notification_title", length = 200)
  private String notificationTitle;

  @Column(name = "notification_body", length = 1000)
  private String notificationBody;

  @Column(name = "enabled", nullable = false)
  private Boolean enabled = true;

  @Column(name = "last_triggered_at")
  private Instant lastTriggeredAt;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @PrePersist
  void createDefaults() {
    if (reminderId == null || reminderId.isBlank()) reminderId = UUID.randomUUID().toString();
    Instant now = Instant.now();
    if (createdAt == null) createdAt = now;
    updatedAt = now;
  }

  @PreUpdate
  void updateTimestamp() {
    updatedAt = Instant.now();
  }
}

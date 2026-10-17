package com.jiajunxie.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;
import lombok.Data;

@Data
@Entity
@TableName("bills.b_business_calendars")
@Table(
    name = "b_business_calendars",
    schema = "bills",
    uniqueConstraints =
        @UniqueConstraint(name = "uk_business_calendar_code", columnNames = "calendar_code"))
public class BusinessCalendar {
  @Id
  @TableId(value = "calendar_id", type = IdType.INPUT)
  @Column(name = "calendar_id", length = 36, nullable = false)
  private String calendarId;

  @Column(name = "calendar_code", length = 50, nullable = false)
  private String calendarCode;

  @Column(name = "calendar_name", length = 100, nullable = false)
  private String calendarName;

  @Column(name = "country_code", length = 10)
  private String countryCode;

  @Column(name = "enabled", nullable = false)
  private Boolean enabled = true;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @PrePersist
  void prePersist() {
    Instant now = Instant.now();
    if (calendarId == null || calendarId.isBlank()) calendarId = UUID.randomUUID().toString();
    if (createdAt == null) createdAt = now;
    updatedAt = now;
  }

  @PreUpdate
  void preUpdate() {
    updatedAt = Instant.now();
  }
}

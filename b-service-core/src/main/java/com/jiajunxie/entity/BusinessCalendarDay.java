package com.jiajunxie.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Data
@Entity
@TableName("bills.b_business_calendar_days")
@Table(
    name = "b_business_calendar_days",
    schema = "bills",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_business_calendar_day",
            columnNames = {"calendar_id", "calendar_date"}),
    indexes = @Index(name = "idx_calendar_days_lookup", columnList = "calendar_id,calendar_date"))
public class BusinessCalendarDay {
  @Id
  @TableId(value = "calendar_day_id", type = IdType.INPUT)
  @Column(name = "calendar_day_id", length = 36, nullable = false)
  private String calendarDayId;

  @Column(name = "calendar_id", length = 36, nullable = false)
  private String calendarId;

  @Column(name = "calendar_date", nullable = false)
  private LocalDate calendarDate;

  @Column(name = "business_day", nullable = false)
  private Boolean businessDay;

  @Column(name = "holiday_name", length = 100)
  private String holidayName;

  @Column(name = "source", length = 50)
  private String source;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @PrePersist
  void prePersist() {
    Instant now = Instant.now();
    if (calendarDayId == null || calendarDayId.isBlank()) calendarDayId = UUID.randomUUID().toString();
    if (createdAt == null) createdAt = now;
    updatedAt = now;
  }

  @PreUpdate
  void preUpdate() {
    updatedAt = Instant.now();
  }

  @JsonIgnore
  @TableField(exist = false)
  @ToString.Exclude
  @EqualsAndHashCode.Exclude
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(
      name = "calendar_id",
      referencedColumnName = "calendar_id",
      insertable = false,
      updatable = false,
      foreignKey = @ForeignKey(name = "fk_calendar_days_calendar"))
  private BusinessCalendar businessCalendar;
}

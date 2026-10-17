package com.jiajunxie.entity;
import com.baomidou.mybatisplus.annotation.IdType;import com.baomidou.mybatisplus.annotation.TableId;import com.baomidou.mybatisplus.annotation.TableName;import jakarta.persistence.*;import java.math.BigDecimal;import java.time.Instant;import java.util.UUID;import lombok.Data;
@Data @Entity @TableName("bills.b_credit_card_statement_items") @Table(name="b_credit_card_statement_items",schema="bills")
public class CreditCardStatementItem {
 @Id @TableId(value="statement_item_id",type=IdType.INPUT) @Column(name="statement_item_id",length=36,nullable=false) private String statementItemId;
 @Column(name="statement_id",length=36,nullable=false) private String statementId; @Column(name="schedule_id",length=36,nullable=false) private String scheduleId; @Column(name="source_transaction_id",length=36,nullable=false) private String sourceTransactionId;
 @Column(name="installment_no",nullable=false) private Integer installmentNo; @Column(name="installment_count",nullable=false) private Integer installmentCount;
 @Column(name="principal_amount",precision=19,scale=4,nullable=false) private BigDecimal principalAmount; @Column(name="fee_amount",precision=19,scale=4,nullable=false) private BigDecimal feeAmount=BigDecimal.ZERO; @Column(name="adjustment_amount",precision=19,scale=4,nullable=false) private BigDecimal adjustmentAmount=BigDecimal.ZERO; @Column(name="billed_amount",precision=19,scale=4,nullable=false) private BigDecimal billedAmount;
 @Column(name="currency",length=10,nullable=false) private String currency; @Column(name="item_status_code",length=30,nullable=false) private String itemStatusCode="OPEN"; @Column(name="created_at",nullable=false,updatable=false) private Instant createdAt;
 @PrePersist void prePersist(){if(statementItemId==null||statementItemId.isBlank())statementItemId=UUID.randomUUID().toString();if(createdAt==null)createdAt=Instant.now();}
}

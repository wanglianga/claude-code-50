package cn.nightpharmacy.domain;

import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.time.LocalDateTime;

/**
 * 次日患者反馈：不良反应 / 漏发药品 / 医保扣费争议。
 * 客服凭同一配药记录回溯审方、出库、支付与签收全过程。
 */
@Entity
@Table(name = "complaints")
public class Complaint {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private DispenseOrder order;

    /** ADVERSE_REACTION 不良反应 / MISSING_DRUG 漏发药品 / INSURANCE_DISPUTE 医保扣费争议 / OTHER */
    @Column(nullable = false, length = 24)
    private String category;

    @Column(nullable = false, length = 1000)
    private String content;

    /** OPEN 处理中 / RESOLVED 已解决 / REJECTED 不成立 */
    @Column(nullable = false, length = 12)
    private String status = "OPEN";

    @Column(length = 1000)
    private String handlingNote;

    @ManyToOne(fetch = FetchType.LAZY)
    private User handler;          // 处理客服

    @ManyToOne(fetch = FetchType.LAZY)
    private User reporter;         // 反馈人（患者）

    /** 追溯定位：直接关联问题环节 */
    @Column(length = 24)
    private String tracedStage;    // REVIEW / OUTBOUND / PAYMENT / DELIVERY

    private LocalDateTime createdAt = LocalDateTime.now();
    private LocalDateTime handledAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public DispenseOrder getOrder() { return order; }
    public void setOrder(DispenseOrder order) { this.order = order; }
    public String getCategory() { return category; }
    public void setCategory(String c) { this.category = c; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getStatus() { return status; }
    public void setStatus(String s) { this.status = s; }
    public String getHandlingNote() { return handlingNote; }
    public void setHandlingNote(String s) { this.handlingNote = s; }
    public User getHandler() { return handler; }
    public void setHandler(User u) { this.handler = u; }
    public User getReporter() { return reporter; }
    public void setReporter(User u) { this.reporter = u; }
    public String getTracedStage() { return tracedStage; }
    public void setTracedStage(String s) { this.tracedStage = s; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime t) { this.createdAt = t; }
    public LocalDateTime getHandledAt() { return handledAt; }
    public void setHandledAt(LocalDateTime t) { this.handledAt = t; }
}

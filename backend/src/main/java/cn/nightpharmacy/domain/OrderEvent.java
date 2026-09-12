package cn.nightpharmacy.domain;

import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.time.LocalDateTime;

/**
 * 配药单全过程事件流——任何一次状态/决策变更都必须说明
 * “为什么继续配药 / 暂停配药 / 改为线下复诊”，用于次日争议追溯。
 */
@Entity
@Table(name = "order_events")
public class OrderEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private DispenseOrder order;

    /** SUBMIT/REVIEW/RETURN/DOCTOR_VERIFY/INSURANCE/FEE/PAY/ISSUE/HANDOVER
     *  ADDRESS_CHANGE/COLD_CHAIN/DELIVER/COMPLAINT/ROLLBACK/PAUSE/RESUME/OFFLINE/... */
    @Column(nullable = false, length = 24)
    private String type;

    /** 事件时单据状态 */
    @Column(length = 24)
    private String fromStatus;
    @Column(length = 24)
    private String toStatus;

    /** CONTINUE / PAUSE / OFFLINE / INFO */
    @Column(length = 10)
    private String decision = "INFO";

    @Column(nullable = false, length = 1000)
    private String reason;

    @Column(length = 40)
    private String actorName;
    @Column(length = 24)
    private String actorRole;

    @Column(length = 200)
    private String attachmentPath;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public OrderEvent() {}
    public OrderEvent(DispenseOrder order, String type, String decision, String reason, User actor) {
        this.order = order;
        this.type = type;
        this.fromStatus = null;
        this.decision = decision;
        this.reason = reason;
        if (actor != null) { this.actorName = actor.getDisplayName(); this.actorRole = actor.getRole(); }
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public DispenseOrder getOrder() { return order; }
    public void setOrder(DispenseOrder order) { this.order = order; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getFromStatus() { return fromStatus; }
    public void setFromStatus(String s) { this.fromStatus = s; }
    public String getToStatus() { return toStatus; }
    public void setToStatus(String s) { this.toStatus = s; }
    public String getDecision() { return decision; }
    public void setDecision(String decision) { this.decision = decision; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public String getActorName() { return actorName; }
    public void setActorName(String a) { this.actorName = a; }
    public String getActorRole() { return actorRole; }
    public void setActorRole(String a) { this.actorRole = a; }
    public String getAttachmentPath() { return attachmentPath; }
    public void setAttachmentPath(String p) { this.attachmentPath = p; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime t) { this.createdAt = t; }
}

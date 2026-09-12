package cn.nightpharmacy.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * 夜间值班药师班次与交接记录。
 */
@Entity
@Table(name = "pharmacist_shifts")
public class PharmacistShift {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private User pharmacist;

    @Column(nullable = false, length = 16)
    private String shiftDate;      // 2026-09-12

    /** NIGHT 夜班（22:00-08:00） */
    @Column(nullable = false, length = 8)
    private String shiftType = "NIGHT";

    @Column(nullable = false)
    private boolean onDuty = true; // 当前是否在岗值班

    private LocalDateTime startedAt = LocalDateTime.now();
    private LocalDateTime endedAt;

    /** 交接给下一班药师 */
    @ManyToOne(fetch = FetchType.LAZY)
    private User handedTo;
    @Column(length = 1000)
    private String handoverSummary;
    private LocalDateTime handedOverAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public User getPharmacist() { return pharmacist; }
    public void setPharmacist(User u) { this.pharmacist = u; }
    public String getShiftDate() { return shiftDate; }
    public void setShiftDate(String d) { this.shiftDate = d; }
    public String getShiftType() { return shiftType; }
    public void setShiftType(String s) { this.shiftType = s; }
    public boolean isOnDuty() { return onDuty; }
    public void setOnDuty(boolean b) { this.onDuty = b; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime t) { this.startedAt = t; }
    public LocalDateTime getEndedAt() { return endedAt; }
    public void setEndedAt(LocalDateTime t) { this.endedAt = t; }
    public User getHandedTo() { return handedTo; }
    public void setHandedTo(User u) { this.handedTo = u; }
    public String getHandoverSummary() { return handoverSummary; }
    public void setHandoverSummary(String s) { this.handoverSummary = s; }
    public LocalDateTime getHandedOverAt() { return handedOverAt; }
    public void setHandedOverAt(LocalDateTime t) { this.handedOverAt = t; }
}

package cn.nightpharmacy.domain;

import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 缺药替代协商：药师发现原处方药库存不足时，提出“同成分、同剂型、不同规格”的替代候选，
 * 由患者确认是否接受；完整记录剂量/频次变化说明、医生可联系性、上一剂服用情况与
 * 医保目录差异；拒绝时保留拒绝原因与附近可购药房建议。
 */
@Entity
@Table(name = "substitution_negotiations")
public class SubstitutionNegotiation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private DispenseOrder order;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private OrderItem item;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private User pharmacist;

    // ---------------- 候选替代药快照 ----------------
    @Column(nullable = false, length = 80)
    private String candidateName;
    @Column(length = 60)
    private String candidateSpec;
    @Column(length = 40)
    private String candidateBatchNo;
    @Column(precision = 12, scale = 2)
    private BigDecimal candidatePrice;
    @Column(length = 8)
    private String candidateCatalog;
    @Column(nullable = false)
    private Long candidateDrugId;

    /** 原药/替代药的成分与剂型（记录为同成分同剂型） */
    @Column(length = 80)
    private String ingredient;
    @Column(length = 30)
    private String dosageForm;

    /** 医保目录差异说明（甲→乙等，及报销影响） */
    @Column(length = 500)
    private String insuranceCatalogDiff;

    // ---------------- 剂量/频次变化（变化必须由药师说明） ----------------
    @Column(nullable = false)
    private boolean dosageChanged;
    @Column(nullable = false)
    private boolean frequencyChanged;
    @Column(length = 500)
    private String pharmacistDosageNote;

    // ---------------- 医生可联系性 / 上一剂服用 ----------------
    /** YES 可联系 / NO 无法联系 / UNKNOWN 暂未联系 */
    @Column(nullable = false, length = 8)
    private String doctorReachable;
    @Column(length = 300)
    private String doctorContactNote;

    @Column(nullable = false)
    private boolean lastDoseTaken;
    @Column(length = 300)
    private String lastDoseNote;

    // ---------------- 患者决策 ----------------
    /** PENDING 待患者确认 / ACCEPTED 已接受 / REJECTED 已拒绝 */
    @Column(nullable = false, length = 10)
    private String status = "PENDING";

    @ManyToOne(fetch = FetchType.LAZY)
    private User decidedBy;
    private LocalDateTime decidedAt;

    /** 拒绝原因（拒绝时必填） */
    @Column(length = 500)
    private String rejectionReason;

    /** 附近可购药房建议快照（JSON 文本） */
    @Column(length = 1000)
    private String nearbyPharmacySuggestion;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public DispenseOrder getOrder() { return order; }
    public void setOrder(DispenseOrder order) { this.order = order; }
    public OrderItem getItem() { return item; }
    public void setItem(OrderItem item) { this.item = item; }
    public User getPharmacist() { return pharmacist; }
    public void setPharmacist(User pharmacist) { this.pharmacist = pharmacist; }
    public String getCandidateName() { return candidateName; }
    public void setCandidateName(String s) { this.candidateName = s; }
    public String getCandidateSpec() { return candidateSpec; }
    public void setCandidateSpec(String s) { this.candidateSpec = s; }
    public String getCandidateBatchNo() { return candidateBatchNo; }
    public void setCandidateBatchNo(String s) { this.candidateBatchNo = s; }
    public BigDecimal getCandidatePrice() { return candidatePrice; }
    public void setCandidatePrice(BigDecimal p) { this.candidatePrice = p; }
    public String getCandidateCatalog() { return candidateCatalog; }
    public void setCandidateCatalog(String s) { this.candidateCatalog = s; }
    public Long getCandidateDrugId() { return candidateDrugId; }
    public void setCandidateDrugId(Long id) { this.candidateDrugId = id; }
    public String getIngredient() { return ingredient; }
    public void setIngredient(String s) { this.ingredient = s; }
    public String getDosageForm() { return dosageForm; }
    public void setDosageForm(String s) { this.dosageForm = s; }
    public String getInsuranceCatalogDiff() { return insuranceCatalogDiff; }
    public void setInsuranceCatalogDiff(String s) { this.insuranceCatalogDiff = s; }
    public boolean isDosageChanged() { return dosageChanged; }
    public void setDosageChanged(boolean b) { this.dosageChanged = b; }
    public boolean isFrequencyChanged() { return frequencyChanged; }
    public void setFrequencyChanged(boolean b) { this.frequencyChanged = b; }
    public String getPharmacistDosageNote() { return pharmacistDosageNote; }
    public void setPharmacistDosageNote(String s) { this.pharmacistDosageNote = s; }
    public String getDoctorReachable() { return doctorReachable; }
    public void setDoctorReachable(String s) { this.doctorReachable = s; }
    public String getDoctorContactNote() { return doctorContactNote; }
    public void setDoctorContactNote(String s) { this.doctorContactNote = s; }
    public boolean isLastDoseTaken() { return lastDoseTaken; }
    public void setLastDoseTaken(boolean b) { this.lastDoseTaken = b; }
    public String getLastDoseNote() { return lastDoseNote; }
    public void setLastDoseNote(String s) { this.lastDoseNote = s; }
    public String getStatus() { return status; }
    public void setStatus(String s) { this.status = s; }
    public User getDecidedBy() { return decidedBy; }
    public void setDecidedBy(User u) { this.decidedBy = u; }
    public LocalDateTime getDecidedAt() { return decidedAt; }
    public void setDecidedAt(LocalDateTime t) { this.decidedAt = t; }
    public String getRejectionReason() { return rejectionReason; }
    public void setRejectionReason(String s) { this.rejectionReason = s; }
    public String getNearbyPharmacySuggestion() { return nearbyPharmacySuggestion; }
    public void setNearbyPharmacySuggestion(String s) { this.nearbyPharmacySuggestion = s; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime t) { this.createdAt = t; }
}

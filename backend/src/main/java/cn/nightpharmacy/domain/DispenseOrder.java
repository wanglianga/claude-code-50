package cn.nightpharmacy.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 夜间急症配药单——患者、药师、收银、仓管、配送员、客服共用的同一张协同单据。
 */
@Entity
@Table(name = "dispense_orders")
public class DispenseOrder {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 32)
    private String orderNo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private User patient;

    // ---------------- 患者提交信息 ----------------
    @Column(length = 1000)
    private String symptoms;            // 病症描述

    @Column(length = 500)
    private String allergies;           // 过敏史

    @Column(length = 500)
    private String currentMedication;   // 既往/当前用药

    @Column(length = 300)
    private String prescriptionImagePath; // 电子处方图片

    @Column(length = 60)
    private String prescribingDoctor;   // 开方医生

    @Column(length = 100)
    private String doctorHospital;

    private LocalDate prescriptionDate;
    private LocalDate prescriptionValidUntil; // 处方有效期（系统按日期+7天判断）

    @Column(length = 40)
    private String insuranceNo;         // 医保凭证号

    @Column(nullable = false)
    private boolean needsDelivery;      // 是否需要配送

    @Column(length = 200)
    private String address;
    @Column(length = 30)
    private String contactPhone;
    @Column(length = 30)
    private String recipient;

    // ---------------- 状态与处置决策 ----------------
    /**
     * SUBMITTED 待审方 / PENDING_SUPPLEMENT 退回待补充 / PHARMACIST_REVIEW 审方中
     * DOCTOR_VERIFY 急诊医生电话核实中 / INSURANCE_CHECK 医保核验中 / WAIT_PAYMENT 待支付
     * PAID 已支付 / PICKING 出库中 / DELIVERING 配送中 / DELIVERED 已签收
     * COMPLETED 已完成归档 / PAUSED 暂停配药 / OFFLINE_REFERRAL 转线下复诊 / CANCELLED 已取消
     */
    @Column(nullable = false, length = 24)
    private String status = "SUBMITTED";

    /** 当前处置结论：CONTINUE 继续配药 / PAUSE 暂停配药 / OFFLINE 改为线下复诊 */
    @Column(nullable = false, length = 10)
    private String decision = "CONTINUE";

    /** 暂停前所在状态，恢复时回到该状态 */
    @Column(length = 24)
    private String prevStatus;

    // ---------------- 药师审方 ----------------
    @ManyToOne(fetch = FetchType.LAZY)
    private User pharmacist;

    @Column(length = 1000)
    private String pharmacistOpinion;   // 审方意见（签名/剂量/禁忌/重复用药/替代药）
    private LocalDateTime pharmacistSignedAt;

    // ---------------- 急诊医生电话核实 ----------------
    @Column(length = 60)
    private String doctorVerifiedByName;
    @Column(length = 30)
    private String doctorVerifiedPhone;
    @Column(length = 500)
    private String doctorVerifyNote;
    private LocalDateTime doctorVerifiedAt;

    // ---------------- 医保核验 ----------------
    /** PENDING / APPROVED / REJECTED / ERROR 接口异常 / ROLLED_BACK 已回退 */
    @Column(nullable = false, length = 12)
    private String insuranceStatus = "PENDING";
    @Column(length = 500)
    private String insuranceMessage;
    @Column(nullable = false)
    private boolean chronicFlag = false;   // 是否门诊慢病
    private LocalDateTime insuranceCheckedAt;

    // ---------------- 费用明细 ----------------
    @Column(precision = 12, scale = 2)
    private BigDecimal drugTotal = BigDecimal.ZERO;   // 药品费合计
    @Column(precision = 12, scale = 2)
    private BigDecimal poolingPay = BigDecimal.ZERO;  // 统筹支付
    @Column(precision = 12, scale = 2)
    private BigDecimal chronicPay = BigDecimal.ZERO;  // 门诊慢病额度支付
    @Column(precision = 12, scale = 2)
    private BigDecimal nightFee = BigDecimal.ZERO;    // 夜间服务费
    @Column(precision = 12, scale = 2)
    private BigDecimal selfPay = BigDecimal.ZERO;     // 自费
    @Column(precision = 12, scale = 2)
    private BigDecimal totalAmount = BigDecimal.ZERO; // 应付总额

    @Column(length = 120)
    private String invoiceTitle;        // 发票抬头
    @Column(length = 30)
    private String invoiceTaxNo;
    private LocalDateTime paidAt;
    @Column(length = 20)
    private String paymentMethod;

    // ---------------- 出库 ----------------
    private LocalDateTime outAt;
    @ManyToOne(fetch = FetchType.LAZY)
    private User outBy;
    @Column(length = 500)
    private String outBatchSnapshot;    // 出库批号快照

    // ---------------- 配送/冷链/签收 ----------------
    @ManyToOne(fetch = FetchType.LAZY)
    private User rider;
    @Column(nullable = false)
    private boolean coldChainRequired = false;
    /** 骑手能否保冷：null 未确认 / true 已配冷链箱 / false 无法保冷 */
    private Boolean riderColdCapable;
    @Column(length = 300)
    private String coldPhotoPath;       // 配送温控照片
    @Column(precision = 5, scale = 2)
    private BigDecimal coldTemperature; // 温控记录温度 ℃
    private LocalDateTime deliveredAt;
    @Column(length = 30)
    private String signedBy;            // 签收人
    @Column(length = 20)
    private String signRelation;        // 与患者关系（本人/家属）
    @Column(length = 500)
    private String medicationReminder;  // 用药提醒

    // ---------------- 夜间值班交接 ----------------
    @ManyToOne(fetch = FetchType.LAZY)
    private User handedFrom;
    @ManyToOne(fetch = FetchType.LAZY)
    private User handedTo;
    @Column(length = 500)
    private String handoverNote;
    private LocalDateTime handedOverAt;

    private LocalDateTime createdAt = LocalDateTime.now();
    private LocalDateTime updatedAt = LocalDateTime.now();

    @Version
    private Long version;

    @PreUpdate
    public void onUpdate() { this.updatedAt = LocalDateTime.now(); }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getOrderNo() { return orderNo; }
    public void setOrderNo(String orderNo) { this.orderNo = orderNo; }
    public User getPatient() { return patient; }
    public void setPatient(User patient) { this.patient = patient; }
    public String getSymptoms() { return symptoms; }
    public void setSymptoms(String symptoms) { this.symptoms = symptoms; }
    public String getAllergies() { return allergies; }
    public void setAllergies(String allergies) { this.allergies = allergies; }
    public String getCurrentMedication() { return currentMedication; }
    public void setCurrentMedication(String currentMedication) { this.currentMedication = currentMedication; }
    public String getPrescriptionImagePath() { return prescriptionImagePath; }
    public void setPrescriptionImagePath(String prescriptionImagePath) { this.prescriptionImagePath = prescriptionImagePath; }
    public String getPrescribingDoctor() { return prescribingDoctor; }
    public void setPrescribingDoctor(String prescribingDoctor) { this.prescribingDoctor = prescribingDoctor; }
    public String getDoctorHospital() { return doctorHospital; }
    public void setDoctorHospital(String doctorHospital) { this.doctorHospital = doctorHospital; }
    public LocalDate getPrescriptionDate() { return prescriptionDate; }
    public void setPrescriptionDate(LocalDate prescriptionDate) { this.prescriptionDate = prescriptionDate; }
    public LocalDate getPrescriptionValidUntil() { return prescriptionValidUntil; }
    public void setPrescriptionValidUntil(LocalDate v) { this.prescriptionValidUntil = v; }
    public String getInsuranceNo() { return insuranceNo; }
    public void setInsuranceNo(String insuranceNo) { this.insuranceNo = insuranceNo; }
    public boolean isNeedsDelivery() { return needsDelivery; }
    public void setNeedsDelivery(boolean needsDelivery) { this.needsDelivery = needsDelivery; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public String getContactPhone() { return contactPhone; }
    public void setContactPhone(String contactPhone) { this.contactPhone = contactPhone; }
    public String getRecipient() { return recipient; }
    public void setRecipient(String recipient) { this.recipient = recipient; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getDecision() { return decision; }
    public void setDecision(String decision) { this.decision = decision; }
    public String getPrevStatus() { return prevStatus; }
    public void setPrevStatus(String s) { this.prevStatus = s; }
    public User getPharmacist() { return pharmacist; }
    public void setPharmacist(User pharmacist) { this.pharmacist = pharmacist; }
    public String getPharmacistOpinion() { return pharmacistOpinion; }
    public void setPharmacistOpinion(String pharmacistOpinion) { this.pharmacistOpinion = pharmacistOpinion; }
    public LocalDateTime getPharmacistSignedAt() { return pharmacistSignedAt; }
    public void setPharmacistSignedAt(LocalDateTime t) { this.pharmacistSignedAt = t; }
    public String getDoctorVerifiedByName() { return doctorVerifiedByName; }
    public void setDoctorVerifiedByName(String n) { this.doctorVerifiedByName = n; }
    public String getDoctorVerifiedPhone() { return doctorVerifiedPhone; }
    public void setDoctorVerifiedPhone(String p) { this.doctorVerifiedPhone = p; }
    public String getDoctorVerifyNote() { return doctorVerifyNote; }
    public void setDoctorVerifyNote(String n) { this.doctorVerifyNote = n; }
    public LocalDateTime getDoctorVerifiedAt() { return doctorVerifiedAt; }
    public void setDoctorVerifiedAt(LocalDateTime t) { this.doctorVerifiedAt = t; }
    public String getInsuranceStatus() { return insuranceStatus; }
    public void setInsuranceStatus(String insuranceStatus) { this.insuranceStatus = insuranceStatus; }
    public String getInsuranceMessage() { return insuranceMessage; }
    public void setInsuranceMessage(String insuranceMessage) { this.insuranceMessage = insuranceMessage; }
    public boolean isChronicFlag() { return chronicFlag; }
    public void setChronicFlag(boolean chronicFlag) { this.chronicFlag = chronicFlag; }
    public LocalDateTime getInsuranceCheckedAt() { return insuranceCheckedAt; }
    public void setInsuranceCheckedAt(LocalDateTime t) { this.insuranceCheckedAt = t; }
    public BigDecimal getDrugTotal() { return drugTotal; }
    public void setDrugTotal(BigDecimal v) { this.drugTotal = v; }
    public BigDecimal getPoolingPay() { return poolingPay; }
    public void setPoolingPay(BigDecimal v) { this.poolingPay = v; }
    public BigDecimal getChronicPay() { return chronicPay; }
    public void setChronicPay(BigDecimal v) { this.chronicPay = v; }
    public BigDecimal getNightFee() { return nightFee; }
    public void setNightFee(BigDecimal v) { this.nightFee = v; }
    public BigDecimal getSelfPay() { return selfPay; }
    public void setSelfPay(BigDecimal v) { this.selfPay = v; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal v) { this.totalAmount = v; }
    public String getInvoiceTitle() { return invoiceTitle; }
    public void setInvoiceTitle(String invoiceTitle) { this.invoiceTitle = invoiceTitle; }
    public String getInvoiceTaxNo() { return invoiceTaxNo; }
    public void setInvoiceTaxNo(String invoiceTaxNo) { this.invoiceTaxNo = invoiceTaxNo; }
    public LocalDateTime getPaidAt() { return paidAt; }
    public void setPaidAt(LocalDateTime t) { this.paidAt = t; }
    public String getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(String m) { this.paymentMethod = m; }
    public LocalDateTime getOutAt() { return outAt; }
    public void setOutAt(LocalDateTime t) { this.outAt = t; }
    public User getOutBy() { return outBy; }
    public void setOutBy(User u) { this.outBy = u; }
    public String getOutBatchSnapshot() { return outBatchSnapshot; }
    public void setOutBatchSnapshot(String s) { this.outBatchSnapshot = s; }
    public User getRider() { return rider; }
    public void setRider(User rider) { this.rider = rider; }
    public boolean isColdChainRequired() { return coldChainRequired; }
    public void setColdChainRequired(boolean b) { this.coldChainRequired = b; }
    public Boolean getRiderColdCapable() { return riderColdCapable; }
    public void setRiderColdCapable(Boolean b) { this.riderColdCapable = b; }
    public String getColdPhotoPath() { return coldPhotoPath; }
    public void setColdPhotoPath(String p) { this.coldPhotoPath = p; }
    public BigDecimal getColdTemperature() { return coldTemperature; }
    public void setColdTemperature(BigDecimal t) { this.coldTemperature = t; }
    public LocalDateTime getDeliveredAt() { return deliveredAt; }
    public void setDeliveredAt(LocalDateTime t) { this.deliveredAt = t; }
    public String getSignedBy() { return signedBy; }
    public void setSignedBy(String s) { this.signedBy = s; }
    public String getSignRelation() { return signRelation; }
    public void setSignRelation(String s) { this.signRelation = s; }
    public String getMedicationReminder() { return medicationReminder; }
    public void setMedicationReminder(String s) { this.medicationReminder = s; }
    public User getHandedFrom() { return handedFrom; }
    public void setHandedFrom(User u) { this.handedFrom = u; }
    public User getHandedTo() { return handedTo; }
    public void setHandedTo(User u) { this.handedTo = u; }
    public String getHandoverNote() { return handoverNote; }
    public void setHandoverNote(String s) { this.handoverNote = s; }
    public LocalDateTime getHandedOverAt() { return handedOverAt; }
    public void setHandedOverAt(LocalDateTime t) { this.handedOverAt = t; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime t) { this.createdAt = t; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime t) { this.updatedAt = t; }
    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
}

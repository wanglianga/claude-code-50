package cn.nightpharmacy.domain;

import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 配药单药品明细：含处方剂量、审核结论、出库批号/效期快照、替代情况。
 */
@Entity
@Table(name = "order_items")
public class OrderItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private DispenseOrder order;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Drug drug;

    @Column(nullable = false)
    private Integer quantity = 1;

    /** 处方用法用量，如 "每次1片 每日2次 饭后" */
    @Column(length = 120)
    private String dosage;

    /** 药师对该条目的审核意见 */
    @Column(length = 500)
    private String reviewNote;

    /** CONFIRMED 按方配发 / SUBSTITUTED 替代配发 / REMOVED 删除（缺药且无替代） */
    @Column(nullable = false, length = 12)
    private String fulfillStatus = "PENDING";

    /** 实际配发药品（替代药场景） */
    @Column(length = 80)
    private String fulfilledDrugName;

    /** 实际配发药品 ID（替代协商接受后定位出库用） */
    private Long fulfilledDrugId;

    @Column(precision = 12, scale = 2)
    private BigDecimal unitPrice;

    /** 替代配发时的实际单价/医保目录（甲/乙/否） */
    @Column(precision = 12, scale = 2)
    private BigDecimal fulfilledUnitPrice;
    @Column(length = 8)
    private String fulfilledCatalog;

    // 出库时冻结的批号/效期
    @Column(length = 40)
    private String outBatchNo;
    private LocalDate outExpiryDate;
    private LocalDate nearExpiryFlagDate; // 临期提示：若该批号距效期<=90天记录之

    public OrderItem() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public DispenseOrder getOrder() { return order; }
    public void setOrder(DispenseOrder order) { this.order = order; }
    public Drug getDrug() { return drug; }
    public void setDrug(Drug drug) { this.drug = drug; }
    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }
    public String getDosage() { return dosage; }
    public void setDosage(String dosage) { this.dosage = dosage; }
    public String getReviewNote() { return reviewNote; }
    public void setReviewNote(String reviewNote) { this.reviewNote = reviewNote; }
    public String getFulfillStatus() { return fulfillStatus; }
    public void setFulfillStatus(String s) { this.fulfillStatus = s; }
    public String getFulfilledDrugName() { return fulfilledDrugName; }
    public void setFulfilledDrugName(String s) { this.fulfilledDrugName = s; }
    public Long getFulfilledDrugId() { return fulfilledDrugId; }
    public void setFulfilledDrugId(Long id) { this.fulfilledDrugId = id; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public void setUnitPrice(BigDecimal p) { this.unitPrice = p; }
    public BigDecimal getFulfilledUnitPrice() { return fulfilledUnitPrice; }
    public void setFulfilledUnitPrice(BigDecimal p) { this.fulfilledUnitPrice = p; }
    public String getFulfilledCatalog() { return fulfilledCatalog; }
    public void setFulfilledCatalog(String c) { this.fulfilledCatalog = c; }
    public String getOutBatchNo() { return outBatchNo; }
    public void setOutBatchNo(String s) { this.outBatchNo = s; }
    public LocalDate getOutExpiryDate() { return outExpiryDate; }
    public void setOutExpiryDate(LocalDate d) { this.outExpiryDate = d; }
    public LocalDate getNearExpiryFlagDate() { return nearExpiryFlagDate; }
    public void setNearExpiryFlagDate(LocalDate d) { this.nearExpiryFlagDate = d; }
}

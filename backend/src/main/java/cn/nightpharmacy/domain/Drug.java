package cn.nightpharmacy.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 药品目录与库存批号信息。
 */
@Entity
@Table(name = "drugs")
public class Drug {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 80)
    private String name;

    /** 规格，如 0.25g*24片 */
    @Column(length = 60)
    private String spec;

    @Column(length = 40)
    private String manufacturer;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    /** 管制类别：NORMAL 普通 / OTC 非处方 / RX 处方药 / CONTROLLED 管制（精麻毒放等） */
    @Column(nullable = false, length = 16)
    private String controlCategory = "RX";

    /** 是否需要 2-8℃ 冷藏 */
    @Column(nullable = false)
    private boolean coldChain = false;

    /** 当前库存数量（盒） */
    @Column(nullable = false)
    private Integer stock = 0;

    /** 库存批号 */
    @Column(length = 40)
    private String batchNo;

    /** 批号效期 */
    private LocalDate expiryDate;

    /** 医保目录内：甲类/乙类/否 */
    @Column(length = 8)
    private String insuranceCatalog = "甲";

    /** 替代药建议（药师审方时展示） */
    @Column(length = 200)
    private String alternative;

    /** 常见禁忌 */
    @Column(length = 200)
    private String contraindication;

    @Version
    private Long version;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getSpec() { return spec; }
    public void setSpec(String spec) { this.spec = spec; }
    public String getManufacturer() { return manufacturer; }
    public void setManufacturer(String manufacturer) { this.manufacturer = manufacturer; }
    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }
    public String getControlCategory() { return controlCategory; }
    public void setControlCategory(String controlCategory) { this.controlCategory = controlCategory; }
    public boolean isColdChain() { return coldChain; }
    public void setColdChain(boolean coldChain) { this.coldChain = coldChain; }
    public Integer getStock() { return stock; }
    public void setStock(Integer stock) { this.stock = stock; }
    public String getBatchNo() { return batchNo; }
    public void setBatchNo(String batchNo) { this.batchNo = batchNo; }
    public LocalDate getExpiryDate() { return expiryDate; }
    public void setExpiryDate(LocalDate expiryDate) { this.expiryDate = expiryDate; }
    public String getInsuranceCatalog() { return insuranceCatalog; }
    public void setInsuranceCatalog(String insuranceCatalog) { this.insuranceCatalog = insuranceCatalog; }
    public String getAlternative() { return alternative; }
    public void setAlternative(String alternative) { this.alternative = alternative; }
    public String getContraindication() { return contraindication; }
    public void setContraindication(String contraindication) { this.contraindication = contraindication; }
    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
}

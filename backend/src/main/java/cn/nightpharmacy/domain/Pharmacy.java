package cn.nightpharmacy.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;

/** 附近药房（患者拒绝替代时给出可购原药的 24 小时药房建议）。 */
@Entity
@Table(name = "pharmacies")
public class Pharmacy {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 80)
    private String name;

    @Column(length = 200)
    private String address;

    @Column(length = 30)
    private String phone;

    @Column(nullable = false)
    private boolean open24h = true;

    /** 距患者/本店距离（公里），用于就近推荐 */
    @Column(precision = 5, scale = 1)
    private BigDecimal distanceKm;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public boolean isOpen24h() { return open24h; }
    public void setOpen24h(boolean b) { this.open24h = b; }
    public BigDecimal getDistanceKm() { return distanceKm; }
    public void setDistanceKm(BigDecimal d) { this.distanceKm = d; }
}

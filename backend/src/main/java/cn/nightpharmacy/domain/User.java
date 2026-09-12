package cn.nightpharmacy.domain;

import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.time.LocalDateTime;

/**
 * 平台用户：患者/药师/收银/仓管/配送员/客服/管理员。
 */
@Entity
@Table(name = "users")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 40)
    private String username;

    @JsonIgnore
    @Column(nullable = false, length = 100)
    private String password;

    @Column(nullable = false, length = 40)
    private String displayName;

    /** PATIENT / PHARMACIST / CASHIER / WAREHOUSE / RIDER / CUSTOMER_SERVICE / ADMIN */
    @Column(nullable = false, length = 24)
    private String role;

    @Column(length = 30)
    private String phone;

    /** 药师执业编号（药师角色使用） */
    @Column(length = 40)
    private String licenseNo;

    private LocalDateTime createdAt = LocalDateTime.now();

    public User() {}

    public User(String username, String password, String displayName, String role, String phone) {
        this.username = username;
        this.password = password;
        this.displayName = displayName;
        this.role = role;
        this.phone = phone;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getLicenseNo() { return licenseNo; }
    public void setLicenseNo(String licenseNo) { this.licenseNo = licenseNo; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}

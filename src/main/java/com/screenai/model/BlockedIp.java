package com.screenai.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

/**
 * Entity for tracking blocked IP addresses.
 */
@Table("blocked_ips")
public class BlockedIp {

    @Id
    private Long id;

    @Column("ip_address")
    private String ipAddress;

    @Column("reason")
    private String reason;

    @Column("blocked_at")
    private LocalDateTime blockedAt;

    @Column("blocked_until")
    private LocalDateTime blockedUntil;

    @Column("created_by")
    private String createdBy;

    // Default constructor
    public BlockedIp() {
        this.blockedAt = LocalDateTime.now();
    }

    // Constructor with required fields
    public BlockedIp(String ipAddress, String reason, LocalDateTime blockedUntil) {
        this();
        this.ipAddress = ipAddress;
        this.reason = reason;
        this.blockedUntil = blockedUntil;
    }

    // Check if block is still active
    public boolean isActive() {
        return blockedUntil != null && LocalDateTime.now().isBefore(blockedUntil);
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public LocalDateTime getBlockedAt() {
        return blockedAt;
    }

    public void setBlockedAt(LocalDateTime blockedAt) {
        this.blockedAt = blockedAt;
    }

    public LocalDateTime getBlockedUntil() {
        return blockedUntil;
    }

    public void setBlockedUntil(LocalDateTime blockedUntil) {
        this.blockedUntil = blockedUntil;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    @Override
    public String toString() {
        return "BlockedIp{" +
                "ipAddress='" + ipAddress + '\'' +
                ", reason='" + reason + '\'' +
                ", blockedUntil=" + blockedUntil +
                ", active=" + isActive() +
                '}';
    }
}

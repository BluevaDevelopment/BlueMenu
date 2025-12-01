package net.blueva.menu.common.dto;

import java.util.UUID;

/**
 * Data Transfer Object for editor sessions
 */
public class SessionDTO {
    private String sessionId;
    private long createdAt;
    private long expiresAt;
    private boolean active;
    private boolean consumed;
    private boolean pendingWebBind;
    private boolean requireConfirmation;
    private boolean confirmed;

    public SessionDTO() {
        this.sessionId = UUID.randomUUID().toString();
        this.createdAt = System.currentTimeMillis();
        this.expiresAt = this.createdAt + (3600 * 1000); // 1 hour
        this.active = true;
        this.consumed = false;
        this.pendingWebBind = false;
        this.requireConfirmation = false;
        this.confirmed = false;
    }

    public SessionDTO(String sessionId) {
        this.sessionId = sessionId;
        this.createdAt = System.currentTimeMillis();
        this.expiresAt = this.createdAt + (3600 * 1000);
        this.active = true;
        this.consumed = false;
        this.pendingWebBind = false;
        this.requireConfirmation = false;
        this.confirmed = false;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public long getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(long expiresAt) {
        this.expiresAt = expiresAt;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public boolean isExpired() {
        return System.currentTimeMillis() > expiresAt;
    }

    public boolean isConsumed() {
        return consumed;
    }

    public void setConsumed(boolean consumed) {
        this.consumed = consumed;
    }

    public boolean isPendingWebBind() {
        return pendingWebBind;
    }

    public void setPendingWebBind(boolean pendingWebBind) {
        this.pendingWebBind = pendingWebBind;
    }

    public boolean isRequireConfirmation() {
        return requireConfirmation;
    }

    public void setRequireConfirmation(boolean requireConfirmation) {
        this.requireConfirmation = requireConfirmation;
    }

    public boolean isConfirmed() {
        return confirmed;
    }

    public void setConfirmed(boolean confirmed) {
        this.confirmed = confirmed;
    }
}

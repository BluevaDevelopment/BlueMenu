package net.blueva.menu.common.dto;

import java.util.UUID;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

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
    private boolean confirmed;
    private UUID confirmedBy;
    private boolean requireConfirmation;
    private Set<String> verificationIds;
    private String confirmedVerificationId;
    private String edition;
    private String serverVersion;

    public SessionDTO() {
        this(UUID.randomUUID().toString(), 3600 * 1000L);
    }

    public SessionDTO(String sessionId) {
        this(sessionId, 3600 * 1000L);
    }

    public SessionDTO(long sessionTimeoutMillis) {
        this(UUID.randomUUID().toString(), sessionTimeoutMillis);
    }

    public SessionDTO(String sessionId, long sessionTimeoutMillis) {
        this.sessionId = sessionId;
        this.createdAt = System.currentTimeMillis();
        this.expiresAt = this.createdAt + sessionTimeoutMillis;
        this.active = true;
        this.consumed = false;
        this.pendingWebBind = false;
        this.confirmed = false;
        this.confirmedBy = null;
        this.requireConfirmation = true;
        this.verificationIds = ConcurrentHashMap.newKeySet();
        this.confirmedVerificationId = null;
        this.edition = "plus";
        this.serverVersion = null;
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

    public boolean isConfirmed() {
        return confirmed;
    }

    public void setConfirmed(boolean confirmed) {
        this.confirmed = confirmed;
    }

    public UUID getConfirmedBy() {
        return confirmedBy;
    }

    public void setConfirmedBy(UUID confirmedBy) {
        this.confirmedBy = confirmedBy;
    }

    public boolean isRequireConfirmation() {
        return requireConfirmation;
    }

    public void setRequireConfirmation(boolean requireConfirmation) {
        this.requireConfirmation = requireConfirmation;
    }

    public Set<String> getVerificationIds() {
        return verificationIds;
    }

    public String getConfirmedVerificationId() {
        return confirmedVerificationId;
    }

    public void setConfirmedVerificationId(String confirmedVerificationId) {
        this.confirmedVerificationId = confirmedVerificationId;
    }

    public String getEdition() {
        return edition != null ? edition : "plus";
    }

    public void setEdition(String edition) {
        this.edition = edition;
    }

    public String getServerVersion() {
        return serverVersion;
    }

    public void setServerVersion(String serverVersion) {
        this.serverVersion = serverVersion;
    }
}

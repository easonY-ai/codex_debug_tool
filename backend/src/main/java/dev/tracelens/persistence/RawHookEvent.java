package dev.tracelens.persistence;

/**
 * Raw Hook evidence. This is deliberately mutable only for the database-generated primary key:
 * MyBatis writes the MySQL AUTO_INCREMENT value back after INSERT, allowing the caller to create
 * the normalization job without querying the just-inserted row again.
 */
public final class RawHookEvent {
    private long id;
    private String deliveryId;
    private long observedAt;
    private long receivedAt;
    private String forwarderVersion;
    private String rawJson;
    private String parseStatus;
    private String errorCode;

    public RawHookEvent() { }

    public RawHookEvent(long id, String deliveryId, long observedAt, long receivedAt,
                        String forwarderVersion, String rawJson, String parseStatus, String errorCode) {
        this.id = id;
        this.deliveryId = deliveryId;
        this.observedAt = observedAt;
        this.receivedAt = receivedAt;
        this.forwarderVersion = forwarderVersion;
        this.rawJson = rawJson;
        this.parseStatus = parseStatus;
        this.errorCode = errorCode;
    }

    public long id() { return id; }
    public String deliveryId() { return deliveryId; }
    public long observedAt() { return observedAt; }
    public long receivedAt() { return receivedAt; }
    public String forwarderVersion() { return forwarderVersion; }
    public String rawJson() { return rawJson; }
    public String parseStatus() { return parseStatus; }
    public String errorCode() { return errorCode; }

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }
    public String getDeliveryId() { return deliveryId; }
    public void setDeliveryId(String deliveryId) { this.deliveryId = deliveryId; }
    public long getObservedAt() { return observedAt; }
    public void setObservedAt(long observedAt) { this.observedAt = observedAt; }
    public long getReceivedAt() { return receivedAt; }
    public void setReceivedAt(long receivedAt) { this.receivedAt = receivedAt; }
    public String getForwarderVersion() { return forwarderVersion; }
    public void setForwarderVersion(String forwarderVersion) { this.forwarderVersion = forwarderVersion; }
    public String getRawJson() { return rawJson; }
    public void setRawJson(String rawJson) { this.rawJson = rawJson; }
    public String getParseStatus() { return parseStatus; }
    public void setParseStatus(String parseStatus) { this.parseStatus = parseStatus; }
    public String getErrorCode() { return errorCode; }
    public void setErrorCode(String errorCode) { this.errorCode = errorCode; }
}

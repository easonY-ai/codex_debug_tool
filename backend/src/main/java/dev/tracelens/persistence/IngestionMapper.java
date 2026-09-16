package dev.tracelens.persistence;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import dev.tracelens.domain.hooknormalization.SessionLifecycle;
import dev.tracelens.domain.hooknormalization.ToolLifecycle;
import dev.tracelens.domain.hooknormalization.TurnLifecycle;
import java.util.List;

@Mapper
public interface IngestionMapper {
    SourceFile sourceByPath(String path);
    void insertSource(SourceFile source);
    void updateSource(SourceFile source);
    void insertRecord(RawRecord record);
    List<SourceFile> sources();
    long countRecords();
    List<RawRecord> records(@Param("afterId") long afterId, @Param("limit") int limit,
                            @Param("sourceId") Long sourceId, @Param("parseStatus") String parseStatus);
    int insertRawHookEvent(RawHookEvent event);
    RawHookEvent hookEventByDeliveryId(String deliveryId);
    void insertNormalizationJob(@Param("sourceKind") String sourceKind, @Param("sourceId") long sourceId,
                                @Param("now") long now);
    long countPendingNormalizationJobs();
    long countHookEvents();
    NormalizationJob nextPendingNormalizationJob(@Param("now") long now);
    void markNormalizationRunning(@Param("id") long id, @Param("now") long now);
    void markNormalizationCompleted(@Param("id") long id, @Param("now") long now);
    void markNormalizationFailed(@Param("id") long id, @Param("now") long now, @Param("errorCode") String errorCode);
    RawHookEvent hookEventById(long id);
    void insertHookSession(SessionLifecycle session);
    void updateHookSession(SessionLifecycle session);
    void insertHookTurn(TurnLifecycle turn);
    void updateHookTurn(TurnLifecycle turn);
    void insertHookTool(ToolLifecycle tool);
    void updateHookTool(ToolLifecycle tool);
    void updateHookParseStatus(@Param("id") long id, @Param("status") String status, @Param("errorCode") String errorCode);
    java.util.Map<String, Object> hookSession(String sessionId);
    java.util.Map<String, Object> hookTurn(@Param("sessionId") String sessionId, @Param("turnId") String turnId);
    java.util.Map<String, Object> hookTool(@Param("sessionId") String sessionId, @Param("turnId") String turnId,
                                           @Param("toolUseId") String toolUseId);
    List<java.util.Map<String, Object>> sessionTurns(@Param("query") String query, @Param("limit") int limit,
                                                      @Param("offset") int offset);
    long countSessionTurns(@Param("query") String query);
    void insertRawOtel(@Param("batchId") String batchId, @Param("objectIndex") int objectIndex,@Param("signalType") String signalType,
                       @Param("traceId") String traceId, @Param("spanId") String spanId,
                       @Param("turnId") String turnId, @Param("callId") String callId,
                       @Param("objectKind") String objectKind, @Param("durationMs") Long durationMs,
                       @Param("eventTime") Long eventTime, @Param("receivedAt") long receivedAt,
                       @Param("rawJson") String rawJson, @Param("parseStatus") String parseStatus);
    long countRawOtel(@Param("signalType") String signalType);
    void upsertUnknownFingerprint(@Param("sha") String sha, @Param("shape") String shape, @Param("now") long now);
    void incrementUnknownFingerprint(@Param("id") long id, @Param("now") long now);
    Long unknownFingerprintId(String sha);
    int linkUnknownRecord(@Param("fingerprintId") long fingerprintId, @Param("rawRecordId") long rawRecordId);
    List<java.util.Map<String, Object>> unknownFingerprints(@Param("limit") int limit, @Param("offset") int offset);
    long countUnknownFingerprints();
    java.util.Map<String, Object> unknownFingerprint(long id);
    RawRecord unknownSample(long id);
    Integer nextMappingVersion(long id);
    void insertUnknownMapping(@Param("fingerprintId") long fingerprintId, @Param("version") int version,
        @Param("mappingJson") String mappingJson, @Param("status") String status,
        @Param("validationError") String validationError, @Param("createdAt") long createdAt);
    void updateUnknownMappingStatus(@Param("id") long id, @Param("status") String status);
    void alignExactOtelTools();
    void insertJsonlSupplement(@Param("nodeType") String nodeType,@Param("nodeId") String nodeId,
        @Param("rawRecordId") long rawRecordId,@Param("contentKind") String contentKind,@Param("callId") String callId,
        @Param("mappingLevel") String mappingLevel,@Param("evidenceJson") String evidenceJson,
        @Param("adapterVersion") String adapterVersion,@Param("contentText") String contentText);
    java.util.Map<String,Object> latestUnknownMapping(long id);
    List<RawRecord> unknownRecords(long id);
}

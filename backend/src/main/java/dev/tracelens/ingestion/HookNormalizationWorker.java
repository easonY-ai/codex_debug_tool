package dev.tracelens.ingestion;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.tracelens.persistence.IngestionMapper;
import dev.tracelens.persistence.NormalizationJob;
import dev.tracelens.persistence.RawHookEvent;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.util.Set;

@Service
@ConditionalOnProperty(name = "trace-lens.normalization.enabled", matchIfMissing = true)
public class HookNormalizationWorker {
    private static final Set<String> SUPPORTED = Set.of(
            "SessionStart", "SessionEnd", "SubagentStart", "PreToolUse", "PermissionRequest",
            "PostToolUse", "PreCompact", "PostCompact", "UserPromptSubmit", "SubagentStop", "Stop", "Interrupt");

    private final IngestionMapper mapper;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactions;
    private final Clock clock;

    @Autowired
    public HookNormalizationWorker(IngestionMapper mapper, ObjectMapper objectMapper,
                                   TransactionTemplate transactions) {
        this(mapper, objectMapper, transactions, Clock.systemUTC());
    }

    public HookNormalizationWorker(IngestionMapper mapper, ObjectMapper objectMapper,
                                   TransactionTemplate transactions, Clock clock) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
        this.transactions = transactions;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${trace-lens.normalization.poll-ms:100}")
    public void poll() {
        // 接收 HTTP 请求只做可靠落库；归一化异步执行，避免 Hook 回调等待数据库关联与状态计算。
        processAvailable();
    }

    public boolean processAvailable() {
        long now = clock.millis();
        NormalizationJob job = mapper.nextPendingNormalizationJob(now);
        if (job == null) return false;
        try {
            transactions.executeWithoutResult(status -> normalize(job, now));
        } catch (RuntimeException failure) {
            transactions.executeWithoutResult(status -> {
                mapper.updateHookParseStatus(job.sourceId(), "FAILED", "NORMALIZATION_FAILED");
                mapper.markNormalizationFailed(job.id(), clock.millis(), "NORMALIZATION_FAILED");
            });
        }
        return true;
    }

    private void normalize(NormalizationJob job, long now) {
        mapper.markNormalizationRunning(job.id(), now);
        RawHookEvent source = mapper.hookEventById(job.sourceId());
        if (source == null || !"HOOK".equals(job.sourceKind())) throw new IllegalStateException("missing source");
        JsonNode event;
        try {
            event = objectMapper.readTree(source.rawJson());
        } catch (Exception invalid) {
            throw new IllegalArgumentException("invalid raw event", invalid);
        }
        String eventName = requiredText(event, "hook_event_name");
        String sessionId = requiredText(event, "session_id");
        if (!SUPPORTED.contains(eventName)) {
            mapper.updateHookParseStatus(source.id(), "UNKNOWN", "UNSUPPORTED_HOOK_EVENT");
            mapper.markNormalizationCompleted(job.id(), now);
            return;
        }
        String transcriptPath = nullableText(event, "transcript_path");
        mapper.upsertHookSession(sessionId, transcriptPath, source.observedAt(), eventName);

        // turn_id 只属于具体用户轮次；SessionStart/SessionEnd 等会话级生命周期事件可以没有它，
        // 因而先按可选字段处理。工具事件必须归属某个 Turn，下面会单独拒绝缺失的 turn_id。
        String turnId = nullableText(event, "turn_id");
        if (turnId != null) mapper.upsertHookTurn(sessionId, turnId, source.observedAt(), eventName);

        if ("PreToolUse".equals(eventName) || "PostToolUse".equals(eventName)) {
            if (turnId == null) throw new IllegalArgumentException("missing turn_id");
            String toolUseId = requiredText(event, "tool_use_id");
            String terminal = postState(event);
            mapper.upsertHookTool(sessionId, turnId, toolUseId, nullableText(event, "tool_name"),
                    source.observedAt(), eventName, terminal);
            // OTLP batches may arrive before Hook normalization; correlation must be order independent.
            mapper.alignExactOtelTools();
        }
        mapper.updateHookParseStatus(source.id(), "NORMALIZED", null);
        mapper.markNormalizationCompleted(job.id(), now);
    }

    private static String postState(JsonNode event) {
        if (!"PostToolUse".equals(event.path("hook_event_name").asText())) return "RUNNING";
        JsonNode response = event.path("tool_response");
        if (response.path("exit_code").isIntegralNumber() && response.path("exit_code").asInt() != 0) return "FAILED";
        if (response.path("is_error").asBoolean(false) || response.path("success").isBoolean()
                && !response.path("success").asBoolean()) return "FAILED";
        return "SUCCESS";
    }

    private static String requiredText(JsonNode node, String field) {
        String value = nullableText(node, field);
        if (value == null) throw new IllegalArgumentException("missing " + field);
        return value;
    }

    private static String nullableText(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value != null && value.isTextual() && !value.textValue().isBlank() ? value.textValue() : null;
    }
}

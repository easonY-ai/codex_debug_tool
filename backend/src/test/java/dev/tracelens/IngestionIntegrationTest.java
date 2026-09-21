package dev.tracelens;

import dev.tracelens.config.JsonlProperties;
import dev.tracelens.ingestion.JsonlScanner;
import dev.tracelens.application.execution.NormalizeNextHookDeliveryUseCase;
import dev.tracelens.application.transcriptcontent.SupplementTranscriptContentUseCase;
import dev.tracelens.ingestion.RawLineParser;
import dev.tracelens.persistence.IngestionMapper;
import dev.tracelens.persistence.RawHookEvent;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Comparator;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"analyzer.jsonl.enabled=true", "analyzer.jsonl.automatic=false",
        "analyzer.jsonl.batch-size=2", "analyzer.jsonl.max-line-bytes=1024",
        "trace-lens.normalization.enabled=false"})
@AutoConfigureMockMvc
class IngestionIntegrationTest extends MySqlIntegrationSupport {
    private static final Path ROOT = temporaryRoot();
    private static final Path SESSIONS = ROOT.resolve("sessions");
    @Autowired JsonlScanner scanner;
    @Autowired IngestionMapper mapper;
    @Autowired RawLineParser parser;
    @Autowired JsonlProperties properties;
    @Autowired TransactionTemplate transaction;
    @Autowired NormalizeNextHookDeliveryUseCase hookWorker;
    @Autowired DataSource database;
    @Autowired MockMvc mvc;
    @Autowired SupplementTranscriptContentUseCase transcriptContentUseCase;

    private static Path temporaryRoot() {
        try { return Files.createTempDirectory("trace-lens-synthetic-"); }
        catch (IOException e) { throw new IllegalStateException(e); }
    }

    @DynamicPropertySource static void config(DynamicPropertyRegistry registry) {
        registry.add("analyzer.jsonl.root", ROOT::toString);
    }

    @BeforeEach void reset() throws Exception {
        try (var connection = database.getConnection(); var statement = connection.createStatement()) {
            statement.execute("DELETE FROM trace_projection_checkpoint");
            statement.execute("DELETE FROM trace_turn_view");
            statement.execute("DELETE FROM trace_telemetry_alignment");
            statement.execute("DELETE FROM trace_transcript_evidence_link");
            statement.execute("DELETE FROM telemetry_change");
            statement.execute("DELETE FROM telemetry_record");
            statement.execute("DELETE FROM transcript_change");
            statement.execute("DELETE FROM transcript_unknown_mapping");
            statement.execute("DELETE FROM transcript_unknown_item");
            statement.execute("DELETE FROM transcript_unknown_fingerprint");
            statement.execute("DELETE FROM transcript_item");
            statement.execute("DELETE FROM transcript");
            statement.execute("DELETE FROM execution_change");
            statement.execute("DELETE FROM execution_normalization_job");
            statement.execute("DELETE FROM execution_tool_call");
            statement.execute("DELETE FROM execution_turn");
            statement.execute("DELETE FROM execution_session");
            statement.execute("DELETE FROM execution_raw_hook_event");
        }
        if (Files.exists(SESSIONS)) {
            try (var paths = Files.walk(SESSIONS)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(path);
            }
        }
        Files.createDirectories(SESSIONS);
    }

    @Test void appendPartialUtf8AndRestartAreIdempotent() throws Exception {
        Path file = SESSIONS.resolve("synthetic.jsonl");
        byte[] first = "{\"type\":\"demo\",\"text\":\"虚构测试\"}\r\n".getBytes(StandardCharsets.UTF_8);
        Files.write(file, first);
        Files.writeString(file, "{\"type\":", StandardOpenOption.APPEND);
        assertThat(scanner.scan().insertedRecords()).isEqualTo(1);
        assertThat(mapper.sources().get(0).byteOffset()).isEqualTo(first.length);
        assertThat(mapper.records(0, 10, null, null).get(0).rawText()).endsWith("\r");
        assertThat(scanner.scan().insertedRecords()).isZero();
        Files.writeString(file, "\"future_event\"}\n", StandardOpenOption.APPEND);
        try (var restarted = new JsonlScanner(properties, mapper, parser, transaction, false)) {
            assertThat(restarted.scan().insertedRecords()).isEqualTo(1);
            assertThat(restarted.scan().insertedRecords()).isZero();
        }
        assertThat(mapper.countRecords()).isEqualTo(2);
        assertThat(mapper.sources().get(0).byteOffset()).isEqualTo(Files.size(file));
    }

    @Test void malformedAndUnknownLinesAreRetainedAndDoNotBlockFollowingLines() throws Exception {
        Path file = SESSIONS.resolve("mixed.jsonl");
        Files.write(file, "not json\n{\"type\":\"future_event\"}\n".getBytes(StandardCharsets.UTF_8));
        Files.write(file, new byte[]{(byte) 0xc3, '\n'}, StandardOpenOption.APPEND);
        Files.writeString(file, "{}\n", StandardOpenOption.APPEND);
        assertThat(scanner.scan().insertedRecords()).isEqualTo(4);
        assertThat(mapper.records(0, 10, null, null)).extracting("parseStatus")
                .containsExactly("INVALID_JSON", "VALID_JSON", "INVALID_UTF8", "VALID_JSON");
    }

    @Test void truncatedAndReplacedFilesKeepOldGenerations() throws Exception {
        Path file = SESSIONS.resolve("rotate.jsonl");
        Files.writeString(file, "{\"type\":\"original\"}\n");
        scanner.scan();
        Files.writeString(file, "{}\n");
        assertThat(scanner.scan().insertedRecords()).isEqualTo(1);
        assertThat(mapper.sources().get(0).generation()).isEqualTo(1);
        Path replacement = ROOT.resolve("replacement.tmp");
        Files.writeString(replacement, "{\"type\":\"new_file\"}\n");
        Files.move(replacement, file, StandardCopyOption.REPLACE_EXISTING);
        assertThat(scanner.scan().insertedRecords()).isEqualTo(1);
        assertThat(mapper.sources().get(0).generation()).isEqualTo(2);
        assertThat(mapper.countRecords()).isEqualTo(3);
    }

    @Test void detectsRewriteThatRegrowsBeyondOldOffset() throws Exception {
        Path file = SESSIONS.resolve("rewrite.jsonl");
        Files.writeString(file, "{\"type\":\"old\"}\n");
        scanner.scan();
        Files.writeString(file, "{\"type\":\"new-and-longer\"}\n");
        assertThat(scanner.scan().insertedRecords()).isEqualTo(1);
        assertThat(mapper.sources().get(0).generation()).isEqualTo(1);
    }

    @Test void failedBatchRollsBackRecordsAndCheckpointTogether() throws Exception {
        Path file = SESSIONS.resolve("transaction.jsonl");
        Files.writeString(file, "{\"type\":\"ok\"}\n{\"type\":\"fail\"}\n");
        IngestionMapper failingMapper = org.mockito.Mockito.mock(IngestionMapper.class,
                org.mockito.AdditionalAnswers.delegatesTo(mapper));
        org.mockito.Mockito.doAnswer(invocation -> {
            dev.tracelens.persistence.RawRecord record = invocation.getArgument(0);
            if ("fail".equals(record.eventType())) throw new org.springframework.dao.DataIntegrityViolationException("synthetic failure");
            mapper.insertRecord(record);
            return null;
        }).when(failingMapper).insertRecord(org.mockito.ArgumentMatchers.any());
        try (var failingScanner = new JsonlScanner(properties, failingMapper, parser, transaction, false)) {
            assertThat(failingScanner.scan().failedFiles()).isEqualTo(1);
        }
        assertThat(mapper.countRecords()).isZero();
        assertThat(mapper.sources().get(0).byteOffset()).isZero();
        assertThat(scanner.scan().insertedRecords()).isEqualTo(2);
        assertThat(mapper.sources().get(0).byteOffset()).isEqualTo(Files.size(file));
    }

    @Test void oversizedLineDoesNotLosePriorLinesOrBlockOtherFiles() throws Exception {
        Files.writeString(SESSIONS.resolve("large.jsonl"), "{}\n" + "x".repeat(1025) + "\n");
        Files.writeString(SESSIONS.resolve("healthy.jsonl"), "{}\n");
        var result = scanner.scan();
        assertThat(result.failedFiles()).isEqualTo(1);
        assertThat(result.insertedRecords()).isEqualTo(2);
        assertThat(mapper.sources()).allMatch(source -> source.byteOffset() == 3);
        assertThat(scanner.scan().insertedRecords()).isZero();
    }

    @Test void skipsSymlinksAndDiscoversNestedFiles() throws Exception {
        Path outside = ROOT.resolve("outside.tmp");
        Files.writeString(outside, "{}\n");
        Files.createSymbolicLink(SESSIONS.resolve("link.jsonl"), outside);
        Path nested = Files.createDirectories(SESSIONS.resolve("2026/09"));
        Files.writeString(nested.resolve("demo.jsonl"), "{}\n");
        assertThat(scanner.scan().files()).isEqualTo(1);
        assertThat(mapper.countRecords()).isEqualTo(1);
    }

    @Test void concurrentRescansDoNotDuplicateData() throws Exception {
        Files.writeString(SESSIONS.resolve("concurrent.jsonl"), "{}\n".repeat(20));
        var executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(scanner::scan);
            var second = executor.submit(scanner::scan);
            assertThat(first.get().insertedRecords() + second.get().insertedRecords()).isEqualTo(20);
        } finally {
            executor.shutdownNow();
        }
        assertThat(mapper.countRecords()).isEqualTo(20);
    }

    @Test void watcherDiscoversNewDirectoriesAndPeriodicScanRemainsIdempotent() throws Exception {
        try (var live = new JsonlScanner(properties, mapper, parser, transaction, true)) {
            live.start();
            assertThat(live.state().watching()).isTrue();
            Path nested = Files.createDirectories(SESSIONS.resolve("new-directory"));
            Files.writeString(nested.resolve("live.jsonl"), "{}\n");
            long deadline = System.nanoTime() + 5_000_000_000L;
            while (mapper.countRecords() == 0 && System.nanoTime() < deadline) {
                Thread.sleep(50);
                live.watchEvents();
            }
            assertThat(mapper.countRecords()).isEqualTo(1);
            live.periodicScan();
            assertThat(mapper.countRecords()).isEqualTo(1);
        }
    }

    @Test void missingRootIsReportedAndRecoveryClearsConsecutiveErrors() throws Exception {
        Files.delete(SESSIONS);
        assertThat(scanner.scan().failedFiles()).isEqualTo(1);
        assertThat(scanner.state().lastError()).isEqualTo("SCAN_ROOT_UNAVAILABLE");
        Files.createDirectories(SESSIONS);
        scanner.scan();
        assertThat(scanner.state().consecutiveErrors()).isZero();
        assertThat(scanner.state().lastError()).isNull();
    }

    @Test void apiSupportsCursorFiltersAndRejectsInvalidQueries() throws Exception {
        Files.writeString(SESSIONS.resolve("api.jsonl"), "{}\ninvalid\n{}\n");
        mvc.perform(post("/api/ingestion/rescan").header("Host", "localhost"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.insertedRecords").value(3));
        long firstId = mapper.records(0, 1, null, null).get(0).id();
        mvc.perform(get("/api/ingestion/records?limit=1").header("Host", "localhost"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.hasMore").value(true))
                .andExpect(jsonPath("$.nextCursor").value(firstId));
        mvc.perform(get("/api/ingestion/records").param("afterId", Long.toString(firstId))
                        .param("parseStatus", "INVALID_JSON").header("Host", "localhost"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].rawText").value("invalid"));
        for (String query : new String[]{"limit=0", "limit=201", "afterId=-1", "sourceId=0", "limit=text", "parseStatus=UNKNOWN"}) {
            mvc.perform(get("/api/ingestion/records?" + query).header("Host", "localhost"))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_QUERY"));
        }
        mvc.perform(get("/api/ingestion/status").header("Host", "localhost"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PARTIAL"))
                .andExpect(jsonPath("$.capabilities.otlp").value("ENABLED"));
    }

    @Test void rejectsCrossOriginAndUntrustedHostRequests() throws Exception {
        mvc.perform(post("/api/ingestion/rescan").header("Host", "localhost").header("Origin", "https://example.com"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/ingestion/records").header("Host", "example.com"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/ingestion/records").header("Host", "localhost").header("Sec-Fetch-Site", "cross-site"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/ingestion/status").header("Host", "127.0.0.1:8080").header("Origin", "http://127.0.0.1:8080"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"));
    }

    @Test void mysqlUsesInnoDbForeignKeysAndIndexedCursor() throws Exception {
        try (var connection = database.getConnection(); var statement = connection.createStatement()) {
            assertThat(connection.getMetaData().getDatabaseProductName()).isEqualTo("MySQL");
            try (var row = statement.executeQuery("SELECT ENGINE FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='transcript_item'")) {
                row.next(); assertThat(row.getString(1)).isEqualTo("InnoDB");
            }
            try (var row = statement.executeQuery("SELECT @@foreign_key_checks")) { row.next(); assertThat(row.getInt(1)).isEqualTo(1); }
            try (var row = statement.executeQuery("EXPLAIN FORMAT=TRADITIONAL SELECT * FROM transcript_item WHERE id > 10 ORDER BY id LIMIT 50")) {
                row.next(); assertThat(row.getString("possible_keys")).contains("PRIMARY");
            }
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> statement.executeUpdate(
                    "INSERT INTO transcript_item(transcript_id,transcript_path,generation,byte_offset,end_offset,content_hash,raw_bytes,raw_text,parse_status,ingested_at) VALUES(-1,'/workspace/demo-project/missing.jsonl',0,0,0,'demo',X'00','demo','VALID_JSON',0)"))
                    .isInstanceOf(java.sql.SQLException.class);
        }
    }

    @Test void hookApiAcceptsOnceAndQueuesNormalizationAtomically() throws Exception {
        String deliveryId = "10000000-0000-4000-8000-000000000001";
        String request = """
                {"schemaVersion":1,"deliveryId":"%s","observedAt":1789368000000,
                 "forwarderVersion":"0.1.0","rawEvent":{"hook_event_name":"SessionStart",
                 "session_id":"session-demo-hook-001","transcript_path":null,"cwd":"/workspace/demo-project","model":"model-demo","source":"startup"}}
                """.formatted(deliveryId);
        var call = post("/api/ingestion/hooks").header("Host", "localhost")
                .header("X-Trace-Lens-Forwarder-Version", "0.1.0")
                .contentType("application/json").content(request);
        mvc.perform(call).andExpect(status().isAccepted())
                .andExpect(jsonPath("$.deliveryId").value(deliveryId))
                .andExpect(jsonPath("$.status").value("ACCEPTED"));
        mvc.perform(call).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DUPLICATE"));
        assertThat(mapper.countHookEvents()).isEqualTo(1);
        assertThat(mapper.countPendingNormalizationJobs()).isEqualTo(1);
        assertThat(mapper.hookEventByDeliveryId(deliveryId).rawJson()).contains("session-demo-hook-001");
        mvc.perform(get("/api/ingestion/hooks/status").header("Host", "localhost"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.accepted").isNumber())
                .andExpect(jsonPath("$.duplicates").isNumber()).andExpect(jsonPath("$.pendingJobs").value(1));
    }

    @Test void insertingRawHookEventBackfillsMySqlGeneratedId() {
        RawHookEvent event = new RawHookEvent(0, "10000000-0000-4000-8000-000000000099", 1000, 1001,
                "0.1.0", "{}", "PENDING", null);

        mapper.insertRawHookEvent(event);

        assertThat(event.id()).isPositive();
    }

    @Test void hookApiRejectsMalformedMismatchedOversizedAndCrossOriginRequests() throws Exception {
        mvc.perform(post("/api/ingestion/hooks").header("Host", "localhost")
                        .header("X-Trace-Lens-Forwarder-Version", "0.1.0")
                        .contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_HOOK_ENVELOPE"));
        String mismatch = """
                {"schemaVersion":1,"deliveryId":"10000000-0000-4000-8000-000000000002","observedAt":1,
                 "forwarderVersion":"0.2.0","rawEvent":{}}
                """;
        mvc.perform(post("/api/ingestion/hooks").header("Host", "localhost")
                        .header("X-Trace-Lens-Forwarder-Version", "0.1.0")
                        .contentType("application/json").content(mismatch))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/ingestion/hooks").header("Host", "localhost")
                        .header("Origin", "https://example.com")
                        .header("X-Trace-Lens-Forwarder-Version", "0.1.0")
                        .contentType("application/json").content(mismatch))
                .andExpect(status().isForbidden());
        byte[] oversized = new byte[1024 * 1024 + 1];
        java.util.Arrays.fill(oversized, (byte) 'x');
        mvc.perform(post("/api/ingestion/hooks").header("Host", "localhost")
                        .header("X-Trace-Lens-Forwarder-Version", "0.1.0")
                        .contentType("application/json").content(oversized))
                .andExpect(status().isPayloadTooLarge());
        assertThat(mapper.countHookEvents()).isZero();
    }

    @Test void hookNormalizationHandlesOutOfOrderToolsAndMonotonicTerminalStates() throws Exception {
        postHook("10000000-0000-4000-8000-000000000011", 2000, """
                {"session_id":"session-demo","transcript_path":"/workspace/demo.jsonl","cwd":"/workspace/demo",
                 "model":"model-demo","hook_event_name":"PostToolUse","turn_id":"turn-demo","tool_use_id":"tool-demo",
                 "tool_name":"Bash","tool_response":{"exit_code":0}}""");
        assertThat(hookWorker.normalizeNextHookDelivery()).isTrue();
        assertThat(mapper.executionToolCall("session-demo", "turn-demo", "tool-demo"))
                .containsEntry("state", "SUCCESS").containsEntry("duration_valid", false);

        postHook("10000000-0000-4000-8000-000000000012", 1000, """
                {"session_id":"session-demo","transcript_path":"/workspace/demo.jsonl","cwd":"/workspace/demo",
                 "model":"model-demo","hook_event_name":"PreToolUse","turn_id":"turn-demo","tool_use_id":"tool-demo",
                 "tool_name":"Bash","tool_input":{}}""");
        hookWorker.normalizeNextHookDelivery();
        assertThat(mapper.executionToolCall("session-demo", "turn-demo", "tool-demo"))
                .containsEntry("state", "SUCCESS").containsEntry("estimated_duration_ms", 1000L)
                .containsEntry("duration_valid", true);

        postHook("10000000-0000-4000-8000-000000000013", 3000, """
                {"session_id":"session-demo","transcript_path":null,"cwd":"/workspace/demo","model":"model-demo",
                 "hook_event_name":"UserPromptSubmit","turn_id":"turn-terminal","prompt":"Synthetic"}""");
        postHook("10000000-0000-4000-8000-000000000014", 4000, """
                {"session_id":"session-demo","transcript_path":null,"cwd":"/workspace/demo","model":"model-demo",
                 "hook_event_name":"Stop","turn_id":"turn-terminal","stop_hook_active":false}""");
        postHook("10000000-0000-4000-8000-000000000015", 5000, """
                {"session_id":"session-demo","transcript_path":null,"cwd":"/workspace/demo","model":"model-demo",
                 "hook_event_name":"UserPromptSubmit","turn_id":"turn-terminal","prompt":"Late duplicate"}""");
        hookWorker.normalizeNextHookDelivery(); hookWorker.normalizeNextHookDelivery(); hookWorker.normalizeNextHookDelivery();
        assertThat(mapper.executionTurn("session-demo", "turn-terminal")).containsEntry("state", "COMPLETED");
    }

    @Test void hookNormalizationRejectsNegativeEstimatedDuration() throws Exception {
        postHook("10000000-0000-4000-8000-000000000021", 1000, """
                {"session_id":"negative-demo","transcript_path":null,"cwd":"/workspace/demo","model":"model-demo",
                 "hook_event_name":"PostToolUse","turn_id":"turn-demo","tool_use_id":"tool-demo","tool_response":{}}""");
        postHook("10000000-0000-4000-8000-000000000022", 2000, """
                {"session_id":"negative-demo","transcript_path":null,"cwd":"/workspace/demo","model":"model-demo",
                 "hook_event_name":"PreToolUse","turn_id":"turn-demo","tool_use_id":"tool-demo","tool_input":{}}""");
        hookWorker.normalizeNextHookDelivery(); hookWorker.normalizeNextHookDelivery();
        assertThat(mapper.executionToolCall("negative-demo", "turn-demo", "tool-demo"))
                .containsEntry("duration_valid", false).doesNotContainKey("estimated_duration_ms");
    }

    @Test
    void equalTurnIdsInDifferentSessionsRemainIsolatedAndPublishSeparateChanges() throws Exception {
        postHook("10000000-0000-4000-8000-000000000023", 1000, """
                {"session_id":"session-a","transcript_path":null,"cwd":"/workspace/demo-a","model":"model-a",
                 "hook_event_name":"UserPromptSubmit","turn_id":"shared-turn","prompt":"Prompt A"}""");
        postHook("10000000-0000-4000-8000-000000000024", 2000, """
                {"session_id":"session-b","transcript_path":null,"cwd":"/workspace/demo-b","model":"model-b",
                 "hook_event_name":"UserPromptSubmit","turn_id":"shared-turn","prompt":"Prompt B"}""");

        hookWorker.normalizeNextHookDelivery();
        hookWorker.normalizeNextHookDelivery();

        assertThat(mapper.executionTurn("session-a", "shared-turn"))
                .containsEntry("title", "Prompt A")
                .containsEntry("working_directory", "/workspace/demo-a");
        assertThat(mapper.executionTurn("session-b", "shared-turn"))
                .containsEntry("title", "Prompt B")
                .containsEntry("working_directory", "/workspace/demo-b");
        try (var connection = database.getConnection();
             var statement = connection.createStatement();
             var rows = statement.executeQuery(
                     "SELECT COUNT(*) FROM execution_change WHERE aggregate_type='TURN' AND turn_id='shared-turn'")) {
            rows.next();
            assertThat(rows.getLong(1)).isEqualTo(2);
        }
    }

    private void postHook(String deliveryId, long observedAt, String rawEvent) throws Exception {
        String request = "{\"schemaVersion\":1,\"deliveryId\":\"" + deliveryId + "\",\"observedAt\":" + observedAt
                + ",\"forwarderVersion\":\"0.1.0\",\"rawEvent\":" + rawEvent + "}";
        mvc.perform(post("/api/ingestion/hooks").header("Host", "localhost")
                        .header("X-Trace-Lens-Forwarder-Version", "0.1.0")
                        .contentType("application/json").content(request))
                .andExpect(status().isAccepted());
    }

    @Test void realQueryAndOtelApisDoNotInventExactAlignmentFromUnverifiedCallIds() throws Exception {
        postHook("10000000-0000-4000-8000-000000000031", 1000, """
          {"session_id":"session-api","transcript_path":null,"cwd":"/workspace/demo-project","model":"model-demo",
           "hook_event_name":"UserPromptSubmit","turn_id":"turn-api","prompt":"Synthetic API acceptance"}""");
        postHook("10000000-0000-4000-8000-000000000032", 2000, """
          {"session_id":"session-api","transcript_path":null,"cwd":"/workspace/demo-project","model":"model-demo",
           "hook_event_name":"PreToolUse","turn_id":"turn-api","tool_use_id":"call-api","tool_name":"Bash","tool_input":{}}""");
        postHook("10000000-0000-4000-8000-000000000033", 5000, """
          {"session_id":"session-api","transcript_path":null,"cwd":"/workspace/demo-project","model":"model-demo",
           "hook_event_name":"PostToolUse","turn_id":"turn-api","tool_use_id":"call-api","tool_name":"Bash","tool_response":{"exit_code":0}}""");
        postHook("10000000-0000-4000-8000-000000000034", 6000, """
          {"session_id":"session-api","transcript_path":null,"cwd":"/workspace/demo-project","model":"model-demo",
           "hook_event_name":"Stop","turn_id":"turn-api","stop_hook_active":false}""");
        while (hookWorker.normalizeNextHookDelivery()) { }
        assertThat(mapper.sessionTurns("", 50, 0)).hasSize(1);
        mvc.perform(get("/api/sessions").header("Host","localhost"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].title").value("Synthetic API acceptance"));
        String otlp="""
          {"resourceSpans":[{"scopeSpans":[{"spans":[{"traceId":"trace-api","spanId":"span-api",
          "name":"shell","startTimeUnixNano":"2000000000","endTimeUnixNano":"5000000000",
          "attributes":[{"key":"codex.call_id","value":{"stringValue":"call-api"}}]}]}]}]}""";
        mvc.perform(post("/v1/traces").header("Host","localhost").contentType("application/json").content(otlp))
                .andExpect(status().isOk());
        mvc.perform(get("/api/sessions/turn-api/analysis").header("Host","localhost"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.performanceSpans.length()").value(0))
                .andExpect(jsonPath("$.alignments.length()").value(0))
                .andExpect(jsonPath("$.aggregates.totalMs").value(5000))
                .andExpect(jsonPath("$.aggregates.toolMs").value(3000))
                .andExpect(jsonPath("$.aggregates.unattributedMs").value(2000));
        mvc.perform(get("/api/ingestion/otel/status").header("Host","localhost"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.traces.stored").value(1));
    }

    @Test void transcriptBindingValidatesSessionMetaAndRetainsUnknownByFingerprint() throws Exception {
        Path transcript=SESSIONS.resolve("bound.jsonl");
        Files.writeString(transcript,"""
          {"type":"session_meta","payload":{"session_id":"session-bound"}}
          {"type":"future_event","payload":{"items":[{"text":"one"},{"text":"two"}]}}
          """);
        String raw="{\"session_id\":\"session-bound\",\"transcript_path\":\""+transcript+"\",\"cwd\":\"/workspace/demo-project\",\"model\":\"model-demo\",\"hook_event_name\":\"SessionStart\"}";
        postHook("10000000-0000-4000-8000-000000000041",1000,raw); hookWorker.normalizeNextHookDelivery();
        transcriptContentUseCase.processAvailable();
        mvc.perform(get("/api/ingestion/transcripts/status").header("Host","localhost"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].pathStatus").value("VALID"))
                .andExpect(jsonPath("$.items[0].sessionCheckStatus").value("MATCHED"));
        mvc.perform(get("/api/unknown-fingerprints").header("Host","localhost"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].canonical_shape").value(org.hamcrest.Matchers.containsString("$.payload.items[*].text:string")));
    }

    @Test void matchedTranscriptSupplementsHookNodesIdempotently() throws Exception {
        Path transcript = SESSIONS.resolve("content.jsonl");
        Files.writeString(transcript, """
          {"type":"session_meta","payload":{"session_id":"session-content"}}
          {"type":"event_msg","payload":{"type":"user_message","turn_id":"turn-content","message":"Synthetic transcript question"}}
          {"type":"response_item","payload":{"type":"function_call","call_id":"tool-content","arguments":"{\\"cmd\\":\\"printf demo\\"}","internal_chat_message_metadata_passthrough":{"turn_id":"turn-jsonl-internal"}}}
          {"type":"response_item","payload":{"type":"function_call_output","call_id":"tool-content","output":"demo","internal_chat_message_metadata_passthrough":{"turn_id":"turn-content"}}}
          {"type":"event_msg","payload":{"type":"task_complete","turn_id":"turn-content","last_agent_message":"Synthetic final answer"}}
          """);
        postHook("20000000-0000-4000-8000-000000000001", 1000, """
          {"session_id":"session-content","transcript_path":"%s","cwd":"/workspace/demo-project","model":"model-demo",
           "hook_event_name":"SessionStart","source":"startup"}""".formatted(transcript));
        postHook("20000000-0000-4000-8000-000000000002", 2000, """
          {"session_id":"session-content","transcript_path":"%s","cwd":"/workspace/demo-project","model":"model-demo",
           "hook_event_name":"UserPromptSubmit","turn_id":"turn-content","prompt":"Hook prompt"}""".formatted(transcript));
        postHook("20000000-0000-4000-8000-000000000003", 3000, """
          {"session_id":"session-content","transcript_path":"%s","cwd":"/workspace/demo-project","model":"model-demo",
           "hook_event_name":"PreToolUse","turn_id":"turn-content","tool_use_id":"tool-content","tool_name":"Bash","tool_input":{}}""".formatted(transcript));
        postHook("20000000-0000-4000-8000-000000000004", 4000, """
          {"session_id":"session-content","transcript_path":"%s","cwd":"/workspace/demo-project","model":"model-demo",
           "hook_event_name":"PostToolUse","turn_id":"turn-content","tool_use_id":"tool-content","tool_name":"Bash","tool_response":{}}""".formatted(transcript));
        postHook("20000000-0000-4000-8000-000000000005", 5000, """
          {"session_id":"session-content","transcript_path":"%s","cwd":"/workspace/demo-project","model":"model-demo",
           "hook_event_name":"Stop","turn_id":"turn-content","stop_hook_active":false}""".formatted(transcript));
        while (hookWorker.normalizeNextHookDelivery()) { }

        transcriptContentUseCase.processAvailable();
        transcriptContentUseCase.processAvailable();

        mvc.perform(get("/api/sessions/turn-content/analysis").header("Host", "localhost"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.session.transcriptPath").doesNotExist())
                .andExpect(jsonPath("$.jsonlSupplements.length()").value(4))
                .andExpect(jsonPath("$.jsonlSupplements[0].contentKind").value("USER_INPUT"))
                .andExpect(jsonPath("$.jsonlSupplements[1].mappingLevel").value("EXACT"))
                .andExpect(jsonPath("$.jsonlSupplements[3].contentText").value("Synthetic final answer"));
        try (var connection = database.getConnection();
             var statement = connection.createStatement();
             var rows = statement.executeQuery("SELECT COUNT(*) FROM trace_transcript_evidence_link")) {
            rows.next();
            assertThat(rows.getLong(1)).isEqualTo(4);
        }
    }
}

package dev.tracelens.ingestion;

import dev.tracelens.config.JsonlProperties;
import dev.tracelens.domain.operationaldiagnostics.AuditedBusinessOperations;
import dev.tracelens.persistence.IngestionMapper;
import dev.tracelens.persistence.RawRecord;
import dev.tracelens.persistence.SourceFile;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;

import static java.nio.file.LinkOption.NOFOLLOW_LINKS;
import static java.nio.file.StandardOpenOption.READ;
import static java.nio.file.StandardWatchEventKinds.*;

@Service
@AuditedBusinessOperations
public class JsonlScanner implements AutoCloseable {
    public record ScanResult(long startedAt, long completedAt, int files, long insertedRecords, int failedFiles) { }
    public record ScannerState(boolean enabled, String root, Long lastStartedAt, Long lastCompletedAt,
                               int consecutiveErrors, String lastError, boolean watching) { }
    private final JsonlProperties properties;
    private final IngestionMapper mapper;
    private final RawLineParser parser;
    private final TransactionTemplate transaction;
    private final boolean automatic;
    private WatchService watcher;
    private final Map<Path, WatchKey> watched = new HashMap<>();
    private Long lastStartedAt;
    private Long lastCompletedAt;
    private int consecutiveErrors;
    private String lastError;

    public JsonlScanner(JsonlProperties properties, IngestionMapper mapper, RawLineParser parser,
                        TransactionTemplate transaction,
                        @Value("${analyzer.jsonl.automatic:true}") boolean automatic) {
        this.properties = properties;
        this.mapper = mapper;
        this.parser = parser;
        this.transaction = transaction;
        this.automatic = automatic;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void start() { if (automatic && properties.enabled()) scan(); }

    @Scheduled(fixedDelayString = "${analyzer.jsonl.scan-interval-ms:10000}", initialDelayString = "${analyzer.jsonl.scan-interval-ms:10000}")
    public void periodicScan() { if (automatic && properties.enabled()) scan(); }

    @Scheduled(fixedDelay = 500, initialDelay = 500)
    public synchronized void watchEvents() {
        if (!automatic || watcher == null) return;
        boolean changed = false;
        WatchKey key;
        while ((key = watcher.poll()) != null) {
            changed |= !key.pollEvents().isEmpty();
            key.reset();
        }
        if (changed) scan(); // New directories are registered by the full incremental discovery pass.
    }

    public synchronized ScannerState state() {
        return new ScannerState(properties.enabled(), properties.root(), lastStartedAt, lastCompletedAt,
                consecutiveErrors, lastError, watcher != null && watched.values().stream().anyMatch(WatchKey::isValid));
    }

    /**
     * 执行一轮全局增量采集：发现 sessions 目录中的所有 JSONL 文件，并让每个文件从上次
     * 已提交的检查点继续入库。单个文件失败不会中断其他文件，最终返回本轮扫描汇总。
     */
    public synchronized ScanResult scan() {
        if (!properties.enabled()) throw new IllegalStateException("INGESTION_DISABLED");

        // 单元素数组允许文件遍历回调更新本轮扫描的累计结果。
        lastStartedAt = System.currentTimeMillis();
        // 本轮成功写入数据库的原始 JSONL 记录总数。
        long[] inserted = {0};
        // 本轮发现并尝试扫描的普通 JSONL 文件总数。
        int[] files = {0};
        // 本轮扫描失败或无法访问的文件总数。
        int[] failed = {0};
        lastError = null;
        try {
            // 只允许从真实且非符号链接的 sessions 目录采集，建立本轮扫描的安全边界。
            Path root = Path.of(properties.root()).toRealPath();
            Path sessions = root.resolve("sessions");
            if (!Files.isDirectory(sessions, NOFOLLOW_LINKS) || Files.isSymbolicLink(sessions)) {
                throw new IOException("SESSIONS_UNAVAILABLE");
            }

            // 遍历同时注册目录监听；只把普通 JSONL 文件交给单文件增量采集逻辑。
            Files.walkFileTree(sessions, new SimpleFileVisitor<>() {
                @Override public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    // 给目录注册监听器，后续通过watchEvents方法通过 WatchService增量触发扫描，避免频繁轮询。
                    register(dir);
                    return FileVisitResult.CONTINUE;
                }
                @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (!attrs.isRegularFile() || !file.getFileName().toString().endsWith(".jsonl")) return FileVisitResult.CONTINUE;
                    files[0]++;
                    // 隔离单文件故障，避免一个损坏或正在变化的文件阻塞整轮采集。
                    try { scanFile(root, file, inserted); }
                    catch (IOException | RuntimeException e) { failed[0]++; lastError = "FILE_SCAN_FAILED"; }
                    return FileVisitResult.CONTINUE;
                }
                @Override public FileVisitResult visitFileFailed(Path file, IOException e) {
                    failed[0]++; lastError = "FILE_UNAVAILABLE";
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException | RuntimeException e) {
            failed[0]++; lastError = "SCAN_ROOT_UNAVAILABLE";
        }

        // 记录整轮结果，供状态接口展示采集健康度和连续失败次数。
        lastCompletedAt = System.currentTimeMillis();
        consecutiveErrors = failed[0] == 0 ? 0 : consecutiveErrors + 1;
        return new ScanResult(lastStartedAt, lastCompletedAt, files[0], inserted[0], failed[0]);
    }

    private void register(Path directory) {
        if (!automatic) return;
        try {
            if (watcher == null) watcher = FileSystems.getDefault().newWatchService();
            WatchKey existing = watched.get(directory);
            if (existing == null || !existing.isValid()) {
                watched.put(directory, directory.register(watcher, ENTRY_CREATE, ENTRY_MODIFY, ENTRY_DELETE));
            }
        } catch (IOException ignored) { /* Periodic scan remains available. */ }
    }

    /**
     * 增量采集一个 JSONL 文件：从数据库检查点继续读取尚未确认的完整行，将记录与新检查点
     * 放在同一事务中提交。这样重复扫描不会重复入库，失败或半行写入也不会错误推进读取位置。
     */
    private void scanFile(Path root, Path file, long[] inserted) throws IOException {
        // 再次校验真实路径，确保目录遍历期间发生的链接替换不能把读取范围带出 sessions。
        Path canonical = file.toRealPath();
        if (!canonical.startsWith(root.resolve("sessions")) || Files.isSymbolicLink(file)) throw new IOException("OUTSIDE_ROOT");
        BasicFileAttributes attrs = Files.readAttributes(canonical, BasicFileAttributes.class, NOFOLLOW_LINKS);
        String identity = identity(attrs);
        try (FileChannel channel = FileChannel.open(canonical, READ, NOFOLLOW_LINKS)) {
            // source_file 保存文件身份、代次和 byte offset，是后续增量读取与幂等处理的依据。
            SourceFile previous = mapper.sourceByPath(canonical.toString());
            if (previous == null) {
                SourceFile initial = new SourceFile(0, canonical.toString(), identity, 0, 0, RawLineParser.hash(new byte[0]),
                        attrs.size(), attrs.lastModifiedTime().toMillis(), System.currentTimeMillis());
                transaction.executeWithoutResult(status -> mapper.insertSource(initial));
                previous = mapper.sourceByPath(canonical.toString());
            }

            // 文件被替换、截断或检查点前内容改变时，开启新代次并从头读取；否则续读检查点。
            boolean reset = !previous.fileKey().equals(identity) || channel.size() < previous.byteOffset()
                    || !anchor(channel, previous.byteOffset()).equals(previous.anchorHash());
            int generation = previous.generation() + (reset ? 1 : 0);
            long position = reset ? 0 : previous.byteOffset();
            long lineStart = position;
            long committed = position;
            SourceFile source = previous;
            // 固定本轮读取上限，避免持续追加的生产者让一次扫描永远无法结束。
            long limit = channel.size();
            channel.position(position);
            ByteBuffer buffer = ByteBuffer.allocate(65536);
            ByteArrayOutputStream line = new ByteArrayOutputStream();
            List<RawRecord> batch = new ArrayList<>();
            long batchBytes = 0;
            while (position < limit) {
                buffer.clear();
                buffer.limit((int) Math.min(buffer.capacity(), limit - position));
                if (channel.read(buffer) <= 0) break;
                buffer.flip();
                while (buffer.hasRemaining()) {
                    byte value = buffer.get();
                    position++;
                    if (value == '\n') {
                        // 只有读到 LF 才形成可提交记录；末尾尚未写完的半行留给下一轮。
                        byte[] raw = line.toByteArray(); // Includes CR for CRLF; LF is reflected in endOffset.
                        batch.add(parser.parse(raw, source.id(), generation, lineStart, position));
                        batchBytes += raw.length;
                        line.reset();
                        lineStart = position;
                        if (batch.size() >= properties.batchSize() || batchBytes >= 8 * 1024 * 1024) {
                            // 批量写入记录并在同一事务中推进检查点，失败时两者一起回滚。
                            commit(channel, canonical, source, identity, generation, position, batch);
                            inserted[0] += batch.size();
                            batch.clear(); batchBytes = 0; committed = position;
                        }
                    } else {
                        if (line.size() >= properties.maxLineBytes()) {
                            // 先提交超大行之前的完整记录，但检查点绝不跨过无法解析的超大行。
                            commit(channel, canonical, source, identity, generation, lineStart, batch);
                            inserted[0] += batch.size();
                            throw new IOException("LINE_TOO_LARGE");
                        }
                        line.write(value);
                    }
                }
            }
            // 提交最后一批完整行；offset 使用 lineStart，明确排除文件末尾的未完成行。
            if (!batch.isEmpty() || reset || committed == (reset ? 0 : previous.byteOffset())) {
                commit(channel, canonical, source, identity, generation, lineStart, batch);
                inserted[0] += batch.size();
            }
        }
    }

    private void commit(FileChannel channel, Path path, SourceFile source, String identity, int generation,
                        long offset, List<RawRecord> records) throws IOException {
        BasicFileAttributes current = Files.readAttributes(path, BasicFileAttributes.class, NOFOLLOW_LINKS);
        if (!current.isRegularFile() || !identity(current).equals(identity) || channel.size() < offset) {
            throw new IOException("FILE_CHANGED_DURING_SCAN");
        }
        SourceFile checkpoint = new SourceFile(source.id(), path.toString(), identity, generation, offset,
                anchor(channel, offset), channel.size(), current.lastModifiedTime().toMillis(), System.currentTimeMillis());
        transaction.executeWithoutResult(status -> {
            for (RawRecord record : records) mapper.insertRecord(record);
            mapper.updateSource(checkpoint);
        });
    }

    private static String identity(BasicFileAttributes attrs) {
        return attrs.fileKey() == null ? "created:" + attrs.creationTime().toMillis() : attrs.fileKey().toString();
    }

    private static String anchor(FileChannel channel, long offset) throws IOException {
        int size = (int) Math.min(offset, 4096);
        ByteBuffer bytes = ByteBuffer.allocate(size);
        long start = offset - size;
        while (bytes.hasRemaining()) {
            int read = channel.read(bytes, start + bytes.position());
            if (read <= 0) throw new IOException("CHECKPOINT_UNAVAILABLE");
        }
        return RawLineParser.hash(bytes.array());
    }

    @PreDestroy
    public synchronized void close() throws IOException { if (watcher != null) watcher.close(); }
}

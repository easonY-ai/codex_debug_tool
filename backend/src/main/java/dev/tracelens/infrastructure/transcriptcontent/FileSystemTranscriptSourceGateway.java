package dev.tracelens.infrastructure.transcriptcontent;

import dev.tracelens.application.transcriptcontent.TranscriptSourceGateway;
import dev.tracelens.application.transcriptcontent.TranscriptSourceResolution;
import dev.tracelens.config.JsonlProperties;
import dev.tracelens.domain.transcriptcontent.TranscriptPathStatus;
import dev.tracelens.ingestion.JsonlScanner;
import dev.tracelens.persistence.SourceFile;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;

/** Safe local-file adapter for Hook-provided transcript paths. */
@Component
public class FileSystemTranscriptSourceGateway implements TranscriptSourceGateway {
    private final JsonlProperties properties;
    private final JsonlScanner jsonlScanner;
    private final TranscriptContentMapper transcriptContentMapper;

    public FileSystemTranscriptSourceGateway(
            JsonlProperties properties,
            JsonlScanner jsonlScanner,
            TranscriptContentMapper transcriptContentMapper) {
        this.properties = properties;
        this.jsonlScanner = jsonlScanner;
        this.transcriptContentMapper = transcriptContentMapper;
    }

    @Override
    public boolean enabled() {
        return properties.enabled();
    }

    @Override
    public void refresh() {
        jsonlScanner.scan();
    }

    @Override
    public TranscriptSourceResolution resolve(String configuredPath) {
        if (configuredPath == null || configuredPath.isBlank()) {
            return unresolved(TranscriptPathStatus.EMPTY);
        }
        try {
            Path configuredRoot = Path.of(properties.root()).toAbsolutePath().normalize();
            Path configuredSessions = configuredRoot.resolve("sessions");
            if (Files.isSymbolicLink(configuredSessions)) {
                return unresolved(TranscriptPathStatus.OUTSIDE_ROOT_OR_SYMLINK);
            }
            Path allowedSessions = configuredSessions.toRealPath();
            Path requested = Path.of(configuredPath).toAbsolutePath().normalize();
            if (!requested.startsWith(configuredSessions)
                    || containsSymlink(requested, configuredSessions)) {
                return unresolved(TranscriptPathStatus.OUTSIDE_ROOT_OR_SYMLINK);
            }
            if (!Files.exists(requested, LinkOption.NOFOLLOW_LINKS)) {
                return unresolved(TranscriptPathStatus.MISSING);
            }
            if (!Files.isRegularFile(requested, LinkOption.NOFOLLOW_LINKS)
                    || !Files.isReadable(requested)) {
                return unresolved(TranscriptPathStatus.UNREADABLE);
            }
            Path canonical = requested.toRealPath();
            if (!canonical.startsWith(allowedSessions)) {
                return unresolved(TranscriptPathStatus.OUTSIDE_ROOT_OR_SYMLINK);
            }
            SourceFile sourceFile = transcriptContentMapper.sourceByPath(canonical.toString());
            if (sourceFile == null) {
                return new TranscriptSourceResolution(
                        TranscriptPathStatus.VALID,
                        canonical.toString(),
                        null,
                        java.util.List.of());
            }
            return new TranscriptSourceResolution(
                    TranscriptPathStatus.VALID,
                    canonical.toString(),
                    sourceFile.id(),
                    transcriptContentMapper.recordsForSource(sourceFile.id()));
        } catch (Exception unavailable) {
            return unresolved(TranscriptPathStatus.UNREADABLE);
        }
    }

    private static TranscriptSourceResolution unresolved(TranscriptPathStatus status) {
        return new TranscriptSourceResolution(status, null, null, java.util.List.of());
    }

    private static boolean containsSymlink(Path requested, Path allowedSessions) {
        Path cursor = requested;
        while (cursor != null && cursor.startsWith(allowedSessions)) {
            if (Files.isSymbolicLink(cursor)) {
                return true;
            }
            if (cursor.equals(allowedSessions)) {
                break;
            }
            cursor = cursor.getParent();
        }
        return false;
    }
}

package dev.tracelens.infrastructure.transcriptcontent;

import dev.tracelens.config.JsonlProperties;
import dev.tracelens.domain.transcriptcontent.TranscriptPathStatus;
import dev.tracelens.ingestion.JsonlScanner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class FileSystemTranscriptSourceGatewayTest {
    @TempDir Path root;

    @Test
    void distinguishesEmptyMissingOutsideSymlinkAndValidPaths() throws Exception {
        Path sessions = Files.createDirectories(root.resolve("sessions"));
        Path valid = Files.writeString(sessions.resolve("valid.jsonl"), "{}\n");
        Path outside = Files.writeString(root.resolve("outside.jsonl"), "{}\n");
        Path symlink = sessions.resolve("linked.jsonl");
        Files.createSymbolicLink(symlink, valid);
        FileSystemTranscriptSourceGateway gateway = new FileSystemTranscriptSourceGateway(
                new JsonlProperties(true, root.toString(), 1000, 1024, 10),
                mock(JsonlScanner.class),
                mock(TranscriptContentMapper.class));

        assertThat(gateway.resolve(" ").pathStatus()).isEqualTo(TranscriptPathStatus.EMPTY);
        assertThat(gateway.resolve(sessions.resolve("missing.jsonl").toString()).pathStatus())
                .isEqualTo(TranscriptPathStatus.MISSING);
        assertThat(gateway.resolve(outside.toString()).pathStatus())
                .isEqualTo(TranscriptPathStatus.OUTSIDE_ROOT_OR_SYMLINK);
        assertThat(gateway.resolve(symlink.toString()).pathStatus())
                .isEqualTo(TranscriptPathStatus.OUTSIDE_ROOT_OR_SYMLINK);
        assertThat(gateway.resolve(valid.toString()).pathStatus()).isEqualTo(TranscriptPathStatus.VALID);
    }
}

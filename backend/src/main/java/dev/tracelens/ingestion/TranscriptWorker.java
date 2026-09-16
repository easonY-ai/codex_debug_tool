package dev.tracelens.ingestion;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.tracelens.config.JsonlProperties;
import dev.tracelens.domain.operationaldiagnostics.AuditedBusinessOperations;
import dev.tracelens.persistence.IngestionMapper;
import dev.tracelens.persistence.RawRecord;
import dev.tracelens.persistence.SourceFile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

@Service
@AuditedBusinessOperations
public class TranscriptWorker {
    static final String ADAPTER = "codex-2026-09";
    private static final Set<String> KNOWN = Set.of("session_meta", "turn_context", "event_msg", "response_item", "compacted");
    private final JsonlProperties properties; private final JsonlScanner scanner; private final IngestionMapper mapper;
    private final ObjectMapper json; private final TransactionTemplate transactions;
    public TranscriptWorker(JsonlProperties properties, JsonlScanner scanner, IngestionMapper mapper,
                            ObjectMapper json, TransactionTemplate transactions) {
        this.properties=properties; this.scanner=scanner; this.mapper=mapper; this.json=json; this.transactions=transactions;
    }

    @Scheduled(fixedDelayString="${trace-lens.transcripts.poll-ms:1000}") public void poll() { process(); }
    public int process() {
        if (!properties.enabled()) return 0;
        scanner.scan(); int checked=0;
        for (Map<String,Object> session : mapper.sessionsNeedingTranscriptCheck()) {
            check(String.valueOf(session.get("session_id")), (String)session.get("transcript_path")); checked++;
        }
        return checked;
    }
    private void check(String sessionId, String configured) {
        long now=System.currentTimeMillis(); String pathStatus="VALID", checkStatus="PENDING", canonical=null;
        Long sourceId=null, metaId=null; String jsonlSession=null;
        try {
            if (configured==null || configured.isBlank()) { pathStatus="EMPTY"; checkStatus="NOT_CHECKED"; }
            else {
                Path requested=Path.of(configured).toAbsolutePath().normalize();
                Path allowed=Path.of(properties.root()).toRealPath();
                if (containsSymlink(requested, allowed)) { pathStatus="OUTSIDE_ROOT_OR_SYMLINK"; checkStatus="NOT_CHECKED"; }
                else if (!Files.exists(requested)) { pathStatus=requested.startsWith(allowed)?"MISSING":"OUTSIDE_ROOT_OR_SYMLINK"; checkStatus="NOT_CHECKED"; }
                else if (!Files.isRegularFile(requested, LinkOption.NOFOLLOW_LINKS) || !Files.isReadable(requested)) { pathStatus="UNREADABLE"; checkStatus="NOT_CHECKED"; }
                else {
                    Path real=requested.toRealPath(); if(!real.startsWith(allowed)){pathStatus="OUTSIDE_ROOT_OR_SYMLINK";checkStatus="NOT_CHECKED";throw new OutsideRoot();}
                    canonical=real.toString(); SourceFile source=mapper.sourceByPath(canonical);
                    if (source!=null) { sourceId=source.id(); List<RawRecord> records=mapper.recordsForSource(source.id());
                        if (records.isEmpty()) checkStatus="SESSION_META_MISSING";
                        else { RawRecord first=records.get(0); JsonNode meta=json.readTree(first.rawText());
                            if (!"session_meta".equals(meta.path("type").asText()) || !meta.path("payload").path("session_id").isTextual()) checkStatus="SESSION_META_UNSUPPORTED";
                            else { metaId=first.id(); jsonlSession=meta.path("payload").path("session_id").asText();
                                checkStatus=sessionId.equals(jsonlSession)?"MATCHED":"SESSION_ID_MISMATCH";
                                if ("MATCHED".equals(checkStatus)) processRecords(records.subList(1, records.size()), now);
                            }
                        }
                    } else checkStatus="SESSION_META_MISSING";
                }
            }
        } catch (OutsideRoot ignored) { } catch (Exception failure) { if ("VALID".equals(pathStatus)) pathStatus="UNREADABLE"; checkStatus="NOT_CHECKED"; }
        String finalPathStatus=pathStatus, finalCheckStatus=checkStatus, finalCanonical=canonical, finalJsonl=jsonlSession;
        Long finalSource=sourceId, finalMeta=metaId;
        transactions.executeWithoutResult(tx -> mapper.upsertTranscriptBinding(sessionId,configured,finalCanonical,finalPathStatus,
                finalSource,finalMeta,finalJsonl,finalCheckStatus,ADAPTER,now));
    }
    private void processRecords(List<RawRecord> records,long now) throws Exception {
        for (RawRecord record:records) if ("VALID_JSON".equals(record.parseStatus())) {
            JsonNode value=json.readTree(record.rawText());
            if (KNOWN.contains(value.path("type").asText())) { retainKnown(record,value); continue; }
            String shape=shape(value); String sha=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(shape.getBytes(StandardCharsets.UTF_8)));
            transactions.executeWithoutResult(tx->{ mapper.upsertUnknownFingerprint(sha,shape,now); long id=mapper.unknownFingerprintId(sha); if(mapper.linkUnknownRecord(id,record.id())==1)mapper.incrementUnknownFingerprint(id,now); });
        }
    }
    private void retainKnown(RawRecord record,JsonNode value){
        JsonNode payload=value.path("payload"); String callId=text(payload,"call_id");
        String turnId=text(payload.path("internal_chat_message_metadata_passthrough"),"turn_id");
        if(turnId==null)turnId=text(payload,"turn_id"); if(turnId==null&&callId==null)return;
        String nodeType=callId==null?"TURN":"TOOL";String nodeId=callId==null?turnId:callId;
        List<String> content=new ArrayList<>();for(JsonNode text:value.findValues("text"))if(text.isTextual())content.add(text.asText());
        mapper.insertJsonlSupplement(nodeType,nodeId,record.id(),payload.path("type").asText(value.path("type").asText()),callId,
                callId==null?"BOUNDED":"EXACT","{\"adapter\":\"codex-2026-09\"}",ADAPTER,String.join("\n",content));
    }
    private static String text(JsonNode node,String field){JsonNode v=node.get(field);return v!=null&&v.isTextual()?v.asText():null;}
    private static boolean containsSymlink(Path requested,Path allowed){
        Path cursor=requested;while(cursor!=null&&cursor.startsWith(allowed)){if(Files.isSymbolicLink(cursor))return true;if(cursor.equals(allowed))break;cursor=cursor.getParent();}return false;
    }
    static String shape(JsonNode root) { List<String> leaves=new ArrayList<>(); collect(root,"$",leaves); Collections.sort(leaves); return String.join("\n",leaves); }
    private static void collect(JsonNode node,String path,List<String> out) {
        if(node.isObject()) node.fields().forEachRemaining(e->collect(e.getValue(),path+"."+e.getKey(),out));
        else if(node.isArray()) node.forEach(v->collect(v,path+"[*]",out));
        else out.add(path+":"+(node.isTextual()?"string":node.isNumber()?"number":node.isBoolean()?"boolean":"null"));
    }
    private static final class OutsideRoot extends Exception { }
}

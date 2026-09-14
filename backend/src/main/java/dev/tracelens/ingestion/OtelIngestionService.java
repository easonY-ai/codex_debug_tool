package dev.tracelens.ingestion;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.tracelens.persistence.IngestionMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class OtelIngestionService {
    private final ObjectMapper json;
    private final IngestionMapper mapper;
    private final TransactionTemplate transactions;
    private final Map<String, AtomicLong> requests = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> failures = new ConcurrentHashMap<>();
    private final Map<String, Long> lastSuccess = new ConcurrentHashMap<>();

    public OtelIngestionService(ObjectMapper json, IngestionMapper mapper, TransactionTemplate transactions) {
        this.json = json; this.mapper = mapper; this.transactions = transactions;
    }

    public void accept(String signal, byte[] body) {
        requests.computeIfAbsent(signal, ignored -> new AtomicLong()).incrementAndGet();
        try {
            JsonNode root = json.readTree(body);
            if (root == null || !root.isObject()) throw new IllegalArgumentException();
            long now = System.currentTimeMillis(); String batch=UUID.randomUUID().toString(); List<JsonNode> objects=objects(root,signal);
            if(objects.isEmpty())throw new IllegalArgumentException();
            transactions.executeWithoutResult(status->{for(int index=0;index<objects.size();index++){JsonNode object=objects.get(index);
                String callId=findAttribute(object,"codex.call_id"); String turnId=findAttribute(object,"codex.turn_id");
                String kind=findText(object,"name"); Long start=findNanos(object,"startTimeUnixNano"), end=findNanos(object,"endTimeUnixNano");
                Long duration=start!=null&&end!=null&&end>=start?end-start:null;
                mapper.insertRawOtel(batch,index,signal,findText(object,"traceId"),findText(object,"spanId"),turnId,callId,kind,duration,
                        start!=null?start:findNanos(object,"timeUnixNano"),now,object.toString(),"VALID");}});
            transactions.executeWithoutResult(status -> mapper.alignExactOtelTools());
            lastSuccess.put(signal, now);
        } catch (Exception invalid) {
            failures.computeIfAbsent(signal, ignored -> new AtomicLong()).incrementAndGet();
            throw new IllegalArgumentException("INVALID_OTLP_JSON");
        }
    }

    public Map<String, Object> status() {
        Map<String, Object> result = new LinkedHashMap<>();
        for (String signal : new String[]{"logs", "traces", "metrics"}) {
            long count = requests.getOrDefault(signal, new AtomicLong()).get();
            long failed = failures.getOrDefault(signal, new AtomicLong()).get();
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("status", count == 0 ? "IDLE" : "READY"); item.put("requests", count);
            item.put("accepted", count - failed); item.put("failed", failed);
            item.put("successRate", count == 0 ? null : (double)(count-failed)/count);
            item.put("lastSuccessAt", lastSuccess.get(signal)); item.put("stored", mapper.countRawOtel(signal));
            result.put(signal, item);
        }
        return result;
    }

    private static String findText(JsonNode node, String name) {
        JsonNode found = node.findValue(name); return found != null && found.isTextual() ? found.asText() : null;
    }
    private static Long findLong(JsonNode node, String name) {
        JsonNode found = node.findValue(name); if (found == null) return null;
        try {
            long value = found.isNumber() ? found.longValue() : Long.parseLong(found.asText());
            return value > 10_000_000_000_000L ? value / 1_000_000L : value;
        }
        catch (NumberFormatException ignored) { return null; }
    }
    private static Long findNanos(JsonNode node,String name){JsonNode found=node.findValue(name);if(found==null)return null;try{return Long.parseLong(found.asText())/1_000_000L;}catch(NumberFormatException ignored){return null;}}
    private static String findAttribute(JsonNode root,String key){
        for(JsonNode attribute:root.findValues("attributes").stream().flatMap(n->n.isArray()?java.util.stream.StreamSupport.stream(n.spliterator(),false):java.util.stream.Stream.empty()).toList())
            if(key.equals(attribute.path("key").asText())){JsonNode value=attribute.path("value");for(String field:new String[]{"stringValue","intValue"})if(value.has(field))return value.path(field).asText();}
        return null;
    }
    private static List<JsonNode> objects(JsonNode root,String signal){String collection=switch(signal){case"traces"->"spans";case"logs"->"logRecords";default->"metrics";};List<JsonNode> out=new java.util.ArrayList<>();for(JsonNode array:root.findValues(collection))if(array.isArray())array.forEach(out::add);return out;}
}

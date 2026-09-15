package dev.tracelens.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.tracelens.persistence.IngestionMapper;
import dev.tracelens.persistence.RawRecord;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

@RestController @RequestMapping("/api/unknown-fingerprints")
public class UnknownController {
    private static final Set<String> FIELDS=Set.of("type","timestamp","payloadType","turnId","callId","role","content");
    private final IngestionMapper mapper; private final ObjectMapper json;
    public UnknownController(IngestionMapper mapper,ObjectMapper json){this.mapper=mapper;this.json=json;}
    @GetMapping public Map<String,Object> list(@RequestParam(defaultValue="50")int limit,@RequestParam(defaultValue="0")int offset){
        if(limit<1||limit>200||offset<0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"INVALID_QUERY");
        return Map.of("items",mapper.unknownFingerprints(limit,offset),"total",mapper.countUnknownFingerprints());
    }
    @GetMapping("/{id}") public Map<String,Object> detail(@PathVariable long id) throws Exception {
        Map<String,Object> fp=mapper.unknownFingerprint(id); RawRecord sample=mapper.unknownSample(id);
        if(fp==null||sample==null) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"UNKNOWN_FINGERPRINT_NOT_FOUND");
        return Map.of("fingerprint",fp,"sample",json.readTree(sample.rawText()));
    }
    @PutMapping("/{id}/mapping") public Map<String,Object> save(@PathVariable long id,@RequestBody Map<String,String> mapping) throws Exception {
        Map<String,Object> preview=validate(id,mapping,true);
        int version=mapper.nextMappingVersion(id); mapper.insertUnknownMapping(id,version,json.writeValueAsString(mapping),"VALID",null,System.currentTimeMillis());
        mapper.updateUnknownMappingStatus(id,"MAPPED"); return Map.of("version",version,"preview",preview,"status","SAVED");
    }
    @PostMapping("/{id}/mapping/preview") public Map<String,Object> preview(@PathVariable long id,@RequestBody Map<String,String> mapping)throws Exception{return Map.of("fields",validate(id,mapping,false));}
    private Map<String,Object> validate(long id,Map<String,String> mapping,boolean rejectInvalid)throws Exception{
        if(mapper.unknownFingerprint(id)==null||!FIELDS.containsAll(mapping.keySet())) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"INVALID_MAPPING");
        JsonNode sample=json.readTree(mapper.unknownSample(id).rawText()); Map<String,Object> preview=new LinkedHashMap<>(); boolean invalid=false;
        for(String field:FIELDS){ String path=mapping.getOrDefault(field,""); Result result=evaluate(sample,path); String error=result.error;
            if(error==null&&!field.equals("content")&&result.values.size()>1) error="标量字段最多匹配一个值";
            if(error==null&&field.equals("content")&&result.values.stream().anyMatch(v->!v.isTextual())) error="正文只能匹配字符串";
            if(error==null&&!field.equals("content")&&result.values.stream().anyMatch(JsonNode::isContainerNode)) error="标量字段不能匹配对象或数组";
            invalid|=error!=null; preview.put(field,Map.of("matchCount",result.values.size(),"valueType",type(result.values),"preview",result.values.stream().map(JsonNode::asText).toList(),"error",error==null?"":error)); }
        if(invalid&&rejectInvalid) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"MAPPING_VALIDATION_FAILED");
        return preview;
    }
    @PostMapping("/{id}/renormalize") public Map<String,Object> renormalize(@PathVariable long id)throws Exception{
        if(mapper.unknownFingerprint(id)==null) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"UNKNOWN_FINGERPRINT_NOT_FOUND");
        Map<String,Object> saved=mapper.latestUnknownMapping(id);if(saved==null)throw new ResponseStatusException(HttpStatus.CONFLICT,"MAPPING_REQUIRED");
        @SuppressWarnings("unchecked") Map<String,String> paths=json.readValue(String.valueOf(saved.get("mapping_json")),Map.class);int processed=0;
        for(RawRecord record:mapper.unknownRecords(id)){JsonNode raw=json.readTree(record.rawText());String turn=first(raw,paths.get("turnId")),call=first(raw,paths.get("callId"));
            List<JsonNode> content=evaluate(raw,paths.get("content")).values;if(turn==null&&call==null)continue;String text=String.join("\n",content.stream().filter(JsonNode::isTextual).map(JsonNode::asText).toList());
            mapper.insertJsonlSupplement(call==null?"TURN":"TOOL",call==null?turn:call,record.id(),"MAPPED_UNKNOWN",call,call==null?"BOUNDED":"EXACT","{\"source\":\"manual_mapping\"}","manual-v"+saved.get("version"),text);processed++;}
        return Map.of("fingerprintId",id,"status","COMPLETED","processed",processed);
    }
    record Result(List<JsonNode> values,String error){}
    static Result evaluate(JsonNode root,String path){ if(path==null||!path.startsWith("$"))return new Result(List.of(),"必须以 $ 开始");
        List<JsonNode> nodes=new ArrayList<>(List.of(root)); int i=1;
        while(i<path.length()){ if(path.charAt(i)=='.'){int start=++i;while(i<path.length()&&(Character.isLetterOrDigit(path.charAt(i))||path.charAt(i)=='_'||path.charAt(i)=='-'))i++;
                if(start==i)return new Result(List.of(),"缺少成员名");String name=path.substring(start,i);List<JsonNode> next=new ArrayList<>();for(JsonNode n:nodes)if(n.isObject()&&n.has(name))next.add(n.get(name));nodes=next;
            }else if(path.charAt(i)=='['){int end=path.indexOf(']',i);if(end<0)return new Result(List.of(),"数组选择器缺少 ]");String selector=path.substring(i+1,end);List<JsonNode> next=new ArrayList<>();
                if("*".equals(selector)){for(JsonNode n:nodes)if(n.isArray())n.forEach(next::add);} else if(selector.matches("0|[1-9]\\d*")){int index=Integer.parseInt(selector);for(JsonNode n:nodes)if(n.isArray()&&index<n.size())next.add(n.get(index));}else return new Result(List.of(),"不支持的数组选择器");nodes=next;i=end+1;
            }else return new Result(List.of(),"不支持的语法"); } return new Result(nodes,null); }
    private static String type(List<JsonNode> values){return values.isEmpty()?"EMPTY":values.get(0).getNodeType().name();}
    private static String first(JsonNode root,String path){Result r=evaluate(root,path);return r.error==null&&!r.values.isEmpty()&&r.values.get(0).isValueNode()?r.values.get(0).asText():null;}
}

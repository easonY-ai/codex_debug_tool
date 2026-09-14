package dev.tracelens.api;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.util.concurrent.CopyOnWriteArrayList;
@Service public class EventStreamService {
 private final CopyOnWriteArrayList<SseEmitter> clients=new CopyOnWriteArrayList<>();
 public SseEmitter subscribe(){SseEmitter e=new SseEmitter(0L);clients.add(e);e.onCompletion(()->clients.remove(e));e.onTimeout(()->clients.remove(e));try{e.send(SseEmitter.event().name("ready").data("CONNECTED"));}catch(Exception x){clients.remove(e);}return e;}
 public void publish(Object value){for(SseEmitter e:clients)try{e.send(SseEmitter.event().name("hook").data(value));}catch(Exception x){clients.remove(e);e.complete();}}
}

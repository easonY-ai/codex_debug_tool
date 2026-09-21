package dev.tracelens.infrastructure.operationaldiagnostics;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.lang.reflect.Array;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Collection;
import java.util.Map;

/**
 * Emits a safe call/return audit for Spring-managed business entry points.
 *
 * <p>Only the method identity, argument summaries, return summary, elapsed time and stable result
 * category are logged. The aspect never serializes an argument or result value.</p>
 */
@Aspect
@Component
public class BusinessOperationAuditAspect {
    private static final Logger logger = LoggerFactory.getLogger(BusinessOperationAuditAspect.class);

    @Around("execution(public * *(..)) && ("
            + "@within(dev.tracelens.domain.operationaldiagnostics.AuditedBusinessOperations) "
            + "|| execution(public * dev.tracelens.domain.execution..*Service.*(..)))")
    public Object auditBusinessOperation(ProceedingJoinPoint joinPoint) throws Throwable {
        String operation = joinPoint.getSignature().getDeclaringType().getSimpleName()
                + "." + joinPoint.getSignature().getName();
        String inputSummary = summarizeArguments(joinPoint.getArgs());
        long started = System.nanoTime();
        logger.info("business_operation_request outcome=started operation={} arguments={}", operation, inputSummary);
        try {
            Object result = joinPoint.proceed();
            logger.info("business_operation_response outcome=succeeded operation={} result={} durationMs={}",
                    operation, summarizeValue(result), elapsedMillis(started));
            return result;
        } catch (Throwable failure) {
            logger.warn("business_operation_response outcome=failed operation={} errorCategory={} durationMs={}",
                    operation, failure.getClass().getSimpleName(), elapsedMillis(started));
            throw failure;
        }
    }

    static String summarizeArguments(Object[] arguments) {
        StringBuilder summary = new StringBuilder("[");
        for (int index = 0; index < arguments.length; index++) {
            if (index > 0) {
                summary.append(',');
            }
            summary.append(summarizeValue(arguments[index]));
        }
        return summary.append(']').toString();
    }

    static String summarizeValue(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof CharSequence text) {
            return "String(length=" + text.length() + ",sha256=" + sha256(text.toString()) + ")";
        }
        if (value instanceof Number || value instanceof Boolean || value.getClass().isEnum()) {
            return value.getClass().getSimpleName();
        }
        if (value instanceof byte[] bytes) {
            return "bytes(length=" + bytes.length + ",sha256=" + sha256(new String(bytes, StandardCharsets.ISO_8859_1)) + ")";
        }
        if (value instanceof Collection<?> collection) {
            return "Collection(size=" + collection.size() + ")";
        }
        if (value instanceof Map<?, ?> map) {
            return "Map(size=" + map.size() + ")";
        }
        if (value.getClass().isArray()) {
            return value.getClass().getComponentType().getSimpleName() + "[](length=" + Array.getLength(value) + ")";
        }
        return value.getClass().getSimpleName();
    }

    private static long elapsedMillis(long started) {
        return Math.max(0, (System.nanoTime() - started) / 1_000_000);
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (Exception impossible) {
            return "unavailable";
        }
    }
}

package dev.tracelens.domain.transcriptcontent;

import dev.tracelens.domain.operationaldiagnostics.AuditedBusinessOperations;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Domain service that owns the complete Hook-target selection and idempotent supplement save.
 */
@AuditedBusinessOperations
public class TranscriptContentService {
    private final HookTranscriptTargetRepository hookTranscriptTargetRepository;
    private final JsonlSupplementRepository jsonlSupplementRepository;

    public TranscriptContentService(
            HookTranscriptTargetRepository hookTranscriptTargetRepository,
            JsonlSupplementRepository jsonlSupplementRepository) {
        this.hookTranscriptTargetRepository = hookTranscriptTargetRepository;
        this.jsonlSupplementRepository = jsonlSupplementRepository;
    }

    /**
     * 将一份已通过路径安全和 {@code session_meta} 会话校验的 transcript 内容补充到既有 Hook 骨架。
     *
     * <p>Codex Hook 是 Session、Turn 和 Tool 节点身份的主事实来源，transcript 只能补充可见内容，
     * 不能创建骨架。普通消息仅在其 transcript {@code turn_id} 对应当前 Hook Session 中的既有 Turn
     * 时挂接，并标记为 {@link TranscriptMappingLevel#BOUNDED}，不会按时间或到达顺序猜测归属。</p>
     *
     * <p>工具内容遵循版本化 transcript 契约：同一 JSONL {@code call_id} 的工具输入和输出先组成一组。
     * 真实 Codex transcript 中，同组记录可能携带不同的内部 {@code turn_id}；只有该组能够从已有 Hook
     * Turn 中确定唯一候选时，整组内容才挂到该 Turn。零候选或多个候选都保持未挂接；缺少
     * {@code call_id} 的工具内容只能按自身已有 {@code turn_id} 进行 {@code BOUNDED} 挂接。</p>
     *
     * <p>Hook {@code tool_use_id} 与 transcript {@code call_id} 属于不同命名空间，不默认相等。只有调用方
     * 提供的是已验证该同值契约的版本化适配器结果，并且当前 Session/Turn 中确实存在同 ID 的 Hook Tool
     * 时，内容才挂到 Tool 并标记为 {@link TranscriptMappingLevel#EXACT}；否则只挂到唯一 Turn，标记为
     * {@link TranscriptMappingLevel#BOUNDED}。该适配器前置条件由上游保证，本方法本身不校验版本；接入新
     * 适配器时必须先验证并更新此映射规则。</p>
     *
     * @param sessionId 已通过 transcript {@code session_meta.payload.session_id} 校验的 Hook Session ID
     * @param contents 版本化 transcript 适配器解析出的可见内容
     * @return 按映射规则选中并提交幂等保存的内容数；重复记录也可能计入，不代表数据库新增行数
     */
    public int attachAll(String sessionId, List<ParsedTranscriptContent> contents) {
        // 只认可 Hook 已建立的 Turn；缓存查询结果，避免同一 turn_id 被重复检查。
        Map<String, Boolean> existingTurns = existingTurns(sessionId, contents);

        // 以 transcript call_id 聚合同一工具调用的输入/输出，并收集其中确实存在于 Hook 骨架的 Turn。
        Map<String, Set<String>> hookTurnCandidatesByCallId = new HashMap<>();
        for (ParsedTranscriptContent content : contents) {
            if (content.kind().belongsToTool()
                    && content.callId() != null
                    && Boolean.TRUE.equals(existingTurns.get(content.turnId()))) {
                hookTurnCandidatesByCallId
                        .computeIfAbsent(content.callId(), ignored -> new HashSet<>())
                        .add(content.turnId());
            }
        }

        int attached = 0;
        for (ParsedTranscriptContent content : contents) {
            // 普通或无 call_id 的内容使用自身 Turn；有 call_id 的工具组必须收敛到唯一 Hook Turn。
            String targetTurnId = targetTurnId(
                    content,
                    existingTurns,
                    hookTurnCandidatesByCallId);
            if (targetTurnId != null) {
                save(sessionId, targetTurnId, content);
                attached++;
            }
        }
        return attached;
    }

    private Map<String, Boolean> existingTurns(
            String sessionId,
            List<ParsedTranscriptContent> contents) {
        Map<String, Boolean> existingTurns = new HashMap<>();
        for (ParsedTranscriptContent content : contents) {
            existingTurns.computeIfAbsent(
                    content.turnId(),
                    turnId -> hookTranscriptTargetRepository.turnExists(sessionId, turnId));
        }
        return existingTurns;
    }

    private static String targetTurnId(
            ParsedTranscriptContent content,
            Map<String, Boolean> existingTurns,
            Map<String, Set<String>> hookTurnCandidatesByCallId) {
        if (!content.kind().belongsToTool() || content.callId() == null) {
            return Boolean.TRUE.equals(existingTurns.get(content.turnId()))
                    ? content.turnId()
                    : null;
        }
        Set<String> candidates = hookTurnCandidatesByCallId.getOrDefault(
                content.callId(),
                Set.of());
        return candidates.size() == 1 ? candidates.iterator().next() : null;
    }

    /**
     * 将一条已经完成 Turn 归属判定的 transcript 内容保存为 Hook 节点补充证据。
     *
     * <p>{@code targetTurnId} 是 {@link #attachAll(String, List)} 按当前 Session 中的 Hook Turn
     * 消歧后的结果。本方法不再选择 Turn，只决定内容应精确挂到 Tool，还是有界挂到 Turn。</p>
     *
     * <p>Codex Hook 的 {@code tool_use_id} 与 transcript 的 {@code call_id} 属于不同命名空间，
     * 名称或用途相似不能证明它们表示同一次调用。对工具内容，只有版本化 transcript 适配器已经验证
     * “值相等可作为公共 ID 证据”，并且当前 Session/Turn 下确实存在以该值为 {@code tool_use_id}
     * 的 Hook Tool 时，才保存为 {@code TOOL/EXACT}。本方法只执行同值存在性检查，不校验适配器版本，
     * 因此前置契约必须由上游适配器和 {@link #attachAll(String, List)} 的调用边界保证。</p>
     *
     * <p>普通内容、缺少 {@code call_id} 的工具内容，以及 {@code call_id} 未命中 Hook Tool 的内容，
     * 都保存为 {@code TURN/BOUNDED}。这里不会按工具名、时间接近或到达顺序把候选提升为
     * {@link TranscriptMappingLevel#EXACT}。</p>
     *
     * <p>保存时保留 transcript {@code call_id}、适配器版本、原始记录 ID 和可见正文作为证据。
     * Repository 使用目标节点、原始记录和内容类型的唯一键实现幂等，因此重复补扫不会产生重复记录。</p>
     *
     * @param sessionId 已与 transcript {@code session_meta.payload.session_id} 匹配的 Hook Session ID
     * @param targetTurnId 已由上一步映射规则确定且确实存在的 Hook Turn ID
     * @param content 版本化 transcript 适配器解析出的单条可见内容及其原始证据标识
     */
    private void save(
            String sessionId,
            String targetTurnId,
            ParsedTranscriptContent content) {
        // call_id 与 tool_use_id 并非 Codex 的通用公共 ID；此同值判断只适用于已验证的适配器契约。
        boolean exactTool = content.kind().belongsToTool()
                && content.callId() != null
                && hookTranscriptTargetRepository.toolExists(
                        sessionId,
                        targetTurnId,
                        content.callId());
        String nodeType = exactTool ? "TOOL" : "TURN";
        String nodeId = exactTool ? content.callId() : targetTurnId;
        TranscriptMappingLevel mappingLevel = exactTool
                ? TranscriptMappingLevel.EXACT
                : TranscriptMappingLevel.BOUNDED;

        // Repository 通过原始记录、目标节点和内容类型的唯一键保证重复补扫不会重复写入。
        jsonlSupplementRepository.save(new TranscriptContentSupplement(
                nodeType,
                nodeId,
                content.rawRecordId(),
                content.kind(),
                content.callId(),
                mappingLevel,
                content.adapterVersion(),
                content.text()));
    }
}

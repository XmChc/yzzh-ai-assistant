package cn.ysmp.module.ai.service;

import cn.ysmp.ai.spi.AiChatMemory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 基于 ai_chat_session / ai_chat_message 的会话持久化（含滚动摘要）。
 */
@Slf4j
@RequiredArgsConstructor
public class DbAiChatMemory implements AiChatMemory {

    private final JdbcTemplate jdbcTemplate;

    private volatile Boolean summaryColumnsReady;

    @Override
    public void ensureSession(String sessionId, Long userId, String agentCode, String titleHint) {
        if (sessionId == null || sessionId.isBlank()) {
            return;
        }
        List<Map<String, Object>> exists = jdbcTemplate.queryForList(
                "SELECT id FROM ai_chat_session WHERE session_id=? AND deleted=0 LIMIT 1", sessionId);
        if (exists.isEmpty()) {
            String title = truncate(titleHint, 80);
            jdbcTemplate.update(
                    "INSERT INTO ai_chat_session(session_id, user_id, agent_code, title, deleted) VALUES (?,?,?,?,0)",
                    sessionId, userId, agentCode, title);
            return;
        }
        jdbcTemplate.update(
                "UPDATE ai_chat_session SET agent_code=COALESCE(?, agent_code), update_time=CURRENT_TIMESTAMP "
                        + "WHERE session_id=? AND deleted=0",
                agentCode, sessionId);
    }

    @Override
    public List<StoredMessage> loadRecentMessages(String sessionId, int maxMessages) {
        if (sessionId == null || sessionId.isBlank() || maxMessages <= 0) {
            return List.of();
        }
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id, role, content FROM ("
                        + "SELECT id, role, content FROM ai_chat_message WHERE session_id=? "
                        + "AND role IN ('user','assistant') ORDER BY id DESC LIMIT ?"
                        + ") t ORDER BY id ASC",
                sessionId, maxMessages);
        return mapMessages(rows);
    }

    @Override
    public SummaryState loadSummaryState(String sessionId) {
        if (sessionId == null || sessionId.isBlank() || !hasSummaryColumns()) {
            return SummaryState.empty();
        }
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT context_summary, summary_until_id FROM ai_chat_session "
                            + "WHERE session_id=? AND deleted=0 LIMIT 1",
                    sessionId);
            if (rows.isEmpty()) {
                return SummaryState.empty();
            }
            Map<String, Object> row = rows.get(0);
            String summary = asStr(row.get("context_summary"));
            long until = row.get("summary_until_id") == null
                    ? 0L
                    : ((Number) row.get("summary_until_id")).longValue();
            return new SummaryState(summary, until);
        } catch (Exception e) {
            summaryColumnsReady = false;
            log.debug("[ai] loadSummaryState fallback: {}", e.getMessage());
            return SummaryState.empty();
        }
    }

    @Override
    public List<StoredMessage> loadMessagesAfter(String sessionId, long afterId, int maxMessages) {
        if (sessionId == null || sessionId.isBlank() || maxMessages <= 0) {
            return List.of();
        }
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id, role, content FROM ai_chat_message WHERE session_id=? "
                        + "AND role IN ('user','assistant') AND id>? ORDER BY id ASC LIMIT ?",
                sessionId, afterId, maxMessages);
        return mapMessages(rows);
    }

    @Override
    public void saveSummary(String sessionId, String summary, long untilMessageId) {
        if (sessionId == null || sessionId.isBlank() || !hasSummaryColumns()) {
            return;
        }
        try {
            jdbcTemplate.update(
                    "UPDATE ai_chat_session SET context_summary=?, summary_until_id=?, update_time=CURRENT_TIMESTAMP "
                            + "WHERE session_id=? AND deleted=0",
                    summary, untilMessageId, sessionId);
        } catch (Exception e) {
            summaryColumnsReady = false;
            log.warn("[ai] saveSummary failed (column missing?): {}", e.getMessage());
        }
    }

    @Override
    public void appendMessage(String sessionId, String role, String content, String toolSummary) {
        if (sessionId == null || sessionId.isBlank() || role == null) {
            return;
        }
        jdbcTemplate.update(
                "INSERT INTO ai_chat_message(session_id, role, content, tool_summary) VALUES (?,?,?,?)",
                sessionId, role, content == null ? "" : content, toolSummary);
        jdbcTemplate.update(
                "UPDATE ai_chat_session SET update_time=CURRENT_TIMESTAMP WHERE session_id=? AND deleted=0",
                sessionId);
        if ("user".equals(role) && content != null && !content.isBlank()) {
            jdbcTemplate.update(
                    "UPDATE ai_chat_session SET title=? WHERE session_id=? AND deleted=0 "
                            + "AND (title IS NULL OR title='')",
                    truncate(content, 80), sessionId);
        }
    }

    @Override
    public List<Map<String, Object>> listSessions(Long userId, int limit) {
        int lim = Math.max(1, Math.min(limit, 200));
        List<Map<String, Object>> rows;
        if (userId == null) {
            rows = jdbcTemplate.queryForList(
                    "SELECT session_id, user_id, agent_code, title, create_time, update_time "
                            + "FROM ai_chat_session WHERE deleted=0 ORDER BY update_time DESC LIMIT ?",
                    lim);
        } else {
            rows = jdbcTemplate.queryForList(
                    "SELECT session_id, user_id, agent_code, title, create_time, update_time "
                            + "FROM ai_chat_session WHERE deleted=0 AND user_id=? ORDER BY update_time DESC LIMIT ?",
                    userId, lim);
        }
        List<Map<String, Object>> out = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            Map<String, Object> vo = new HashMap<>();
            vo.put("sessionId", row.get("session_id"));
            vo.put("userId", row.get("user_id"));
            vo.put("agentCode", row.get("agent_code"));
            vo.put("title", row.get("title"));
            vo.put("createTime", row.get("create_time"));
            vo.put("updateTime", row.get("update_time"));
            out.add(vo);
        }
        return out;
    }

    @Override
    public List<Map<String, Object>> listMessages(String sessionId, Long userId) {
        if (sessionId == null || sessionId.isBlank()) {
            return List.of();
        }
        if (!ownsSession(sessionId, userId)) {
            return List.of();
        }
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id, role, content, tool_summary, create_time FROM ai_chat_message "
                        + "WHERE session_id=? ORDER BY id ASC",
                sessionId);
        List<Map<String, Object>> out = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            Map<String, Object> vo = new HashMap<>();
            vo.put("id", row.get("id"));
            vo.put("role", row.get("role"));
            vo.put("content", row.get("content"));
            vo.put("toolSummary", row.get("tool_summary"));
            vo.put("createTime", row.get("create_time"));
            out.add(vo);
        }
        return out;
    }

    @Override
    public void deleteSession(String sessionId, Long userId) {
        if (sessionId == null || sessionId.isBlank()) {
            return;
        }
        if (!ownsSession(sessionId, userId)) {
            return;
        }
        jdbcTemplate.update("UPDATE ai_chat_session SET deleted=1 WHERE session_id=?", sessionId);
    }

    private boolean hasSummaryColumns() {
        Boolean ready = summaryColumnsReady;
        if (ready != null) {
            return ready;
        }
        synchronized (this) {
            if (summaryColumnsReady != null) {
                return summaryColumnsReady;
            }
            try {
                jdbcTemplate.queryForList(
                        "SELECT context_summary, summary_until_id FROM ai_chat_session LIMIT 0");
                summaryColumnsReady = true;
            } catch (Exception e) {
                summaryColumnsReady = false;
                log.warn("[ai] ai_chat_session 缺少 context_summary/summary_until_id，摘要压缩已降级。"
                        + "请执行 schema 升级 ALTER。");
            }
            return summaryColumnsReady;
        }
    }

    private static List<StoredMessage> mapMessages(List<Map<String, Object>> rows) {
        List<StoredMessage> list = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            String role = asStr(row.get("role"));
            String content = asStr(row.get("content"));
            if (role == null || content == null || content.isBlank()) {
                continue;
            }
            long id = row.get("id") == null ? 0L : ((Number) row.get("id")).longValue();
            list.add(new StoredMessage(id, role, content));
        }
        return list;
    }

    private boolean ownsSession(String sessionId, Long userId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT user_id FROM ai_chat_session WHERE session_id=? AND deleted=0 LIMIT 1", sessionId);
        if (rows.isEmpty()) {
            return false;
        }
        if (userId == null) {
            return true;
        }
        Object uid = rows.get(0).get("user_id");
        if (uid == null) {
            return true;
        }
        return userId.equals(((Number) uid).longValue());
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        String t = s.trim().replaceAll("\\s+", " ");
        return t.length() <= max ? t : t.substring(0, max);
    }

    private static String asStr(Object v) {
        return v == null ? null : String.valueOf(v);
    }
}

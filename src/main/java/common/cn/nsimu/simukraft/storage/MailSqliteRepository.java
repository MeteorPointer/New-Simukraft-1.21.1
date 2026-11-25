package common.cn.nsimu.simukraft.storage;

import common.cn.nsimu.simukraft.SimuKraft;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 邮件、玩家教程状态和邮件调度游标的 SQLite 仓储。文本以组件 JSON 保存，由调用方在主线程序列化。 */
public final class MailSqliteRepository {
    /** 全服广播邮件使用的收件人 id。 */
    public static final String BROADCAST_RECIPIENT = "*";

    private final SimuSqliteDatabase database;

    public MailSqliteRepository(SimuSqliteDatabase database) {
        this.database = database;
    }

    /** 一行邮件的原始数据；组件字段仍是 JSON。 */
    public record MailRow(long id, String recipientId, String category, String senderKey, String senderJson,
                          String subjectJson, String bodyJson, long gameTime, boolean unread, boolean outgoing) {
    }

    public void insert(Connection connection, MailRow row, int keepPerRecipient) throws SQLException {
        try (PreparedStatement stmt = connection.prepareStatement(
                "INSERT OR REPLACE INTO mail_messages(id, recipient_id, category, sender_key, sender_json, subject_json, "
                        + "body_json, game_time, unread, outgoing, created_at) VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            stmt.setLong(1, row.id());
            stmt.setString(2, row.recipientId());
            stmt.setString(3, row.category());
            stmt.setString(4, row.senderKey());
            stmt.setString(5, row.senderJson());
            stmt.setString(6, row.subjectJson());
            stmt.setString(7, row.bodyJson());
            stmt.setLong(8, row.gameTime());
            stmt.setInt(9, row.unread() ? 1 : 0);
            stmt.setInt(10, row.outgoing() ? 1 : 0);
            stmt.setLong(11, System.currentTimeMillis());
            stmt.executeUpdate();
        }
        try (PreparedStatement stmt = connection.prepareStatement(
                "DELETE FROM mail_messages WHERE recipient_id = ? AND id NOT IN "
                        + "(SELECT id FROM mail_messages WHERE recipient_id = ? ORDER BY id DESC LIMIT ?)")) {
            stmt.setString(1, row.recipientId());
            stmt.setString(2, row.recipientId());
            stmt.setInt(3, Math.max(1, keepPerRecipient));
            stmt.executeUpdate();
        }
    }

    public void markRead(Connection connection, String recipientId, String category, long maxId) throws SQLException {
        try (PreparedStatement stmt = connection.prepareStatement(
                "UPDATE mail_messages SET unread = 0 WHERE recipient_id = ? AND category = ? AND id <= ? AND unread = 1")) {
            stmt.setString(1, recipientId);
            stmt.setString(2, category);
            stmt.setLong(3, maxId);
            stmt.executeUpdate();
        }
    }

    /** 读取一个收件人最新的若干封邮件，按 id 从新到旧排列；失败返回 null。 */
    public List<MailRow> loadRecipient(String recipientId, int limit) {
        List<MailRow> rows = new ArrayList<>();
        try (Connection connection = database.borrowConnection();
             PreparedStatement stmt = connection.prepareStatement(
                     "SELECT * FROM mail_messages WHERE recipient_id = ? ORDER BY id DESC LIMIT ?")) {
            stmt.setString(1, recipientId);
            stmt.setInt(2, Math.max(1, limit));
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    rows.add(new MailRow(rs.getLong("id"), rs.getString("recipient_id"), rs.getString("category"),
                            rs.getString("sender_key"), rs.getString("sender_json"), rs.getString("subject_json"),
                            rs.getString("body_json"), rs.getLong("game_time"), rs.getInt("unread") != 0,
                            rs.getInt("outgoing") != 0));
                }
            }
        } catch (SQLException exception) {
            SimuKraft.LOGGER.error("Failed to load mail for {}", recipientId, exception);
            return null;
        }
        return rows;
    }

    public long maxId() {
        try (Connection connection = database.borrowConnection();
             PreparedStatement stmt = connection.prepareStatement("SELECT COALESCE(MAX(id), 0) FROM mail_messages");
             ResultSet rs = stmt.executeQuery()) {
            return rs.next() ? rs.getLong(1) : 0L;
        } catch (SQLException exception) {
            SimuKraft.LOGGER.error("Failed to read mail id cursor", exception);
            return -1L;
        }
    }

    public boolean tutorialDone(String playerId) {
        try (Connection connection = database.borrowConnection();
             PreparedStatement stmt = connection.prepareStatement(
                     "SELECT tutorial_done FROM mail_player_state WHERE player_id = ?")) {
            stmt.setString(1, playerId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() && rs.getInt(1) != 0;
            }
        } catch (SQLException exception) {
            SimuKraft.LOGGER.error("Failed to read mail tutorial state for {}", playerId, exception);
            // 读失败时当作已完成，避免库故障让教程反复弹出。
            return true;
        }
    }

    public void saveTutorialDone(Connection connection, String playerId, boolean done) throws SQLException {
        try (PreparedStatement stmt = connection.prepareStatement(
                "INSERT INTO mail_player_state(player_id, tutorial_done) VALUES(?, ?) "
                        + "ON CONFLICT(player_id) DO UPDATE SET tutorial_done = excluded.tutorial_done")) {
            stmt.setString(1, playerId);
            stmt.setInt(2, done ? 1 : 0);
            stmt.executeUpdate();
        }
    }

    /** 读取全部调度游标（周报周数、市民诉求冷却日）；失败返回 null。 */
    public Map<String, Long> loadSchedule() {
        Map<String, Long> values = new HashMap<>();
        try (Connection connection = database.borrowConnection();
             PreparedStatement stmt = connection.prepareStatement("SELECT schedule_key, last_value FROM mail_schedule");
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                values.put(rs.getString(1), rs.getLong(2));
            }
        } catch (SQLException exception) {
            SimuKraft.LOGGER.error("Failed to load mail schedule", exception);
            return null;
        }
        return values;
    }

    public void saveSchedule(Connection connection, String key, long value) throws SQLException {
        try (PreparedStatement stmt = connection.prepareStatement(
                "INSERT INTO mail_schedule(schedule_key, last_value) VALUES(?, ?) "
                        + "ON CONFLICT(schedule_key) DO UPDATE SET last_value = excluded.last_value")) {
            stmt.setString(1, key);
            stmt.setLong(2, value);
            stmt.executeUpdate();
        }
    }
}

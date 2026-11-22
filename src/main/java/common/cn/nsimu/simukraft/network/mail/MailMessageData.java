package common.cn.nsimu.simukraft.network.mail;

/** 客户端邮件列表使用的不可变消息快照。 */
public record MailMessageData(long id, String category, String sender, String subject,
                              String body, long timestamp, boolean unread) {
    public MailMessageData {
        category = category == null || category.isBlank() ? "SYSTEM" : category;
        sender = sender == null ? "" : sender;
        subject = subject == null ? "" : subject;
        body = body == null ? "" : body;
    }

    public MailMessageData markRead() {
        return new MailMessageData(id, category, sender, subject, body, timestamp, false);
    }
}

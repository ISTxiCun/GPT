package com.istxicun.backend.distributed.messaging;

import com.istxicun.backend.common.identity.ApplicationIdentity;
import java.nio.charset.StandardCharsets;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;

public final class OutboxWriter {
  private final DataSource source;
  private final JdbcTemplate jdbc;
  private final MessageCodec codec;
  private final String scope;

  public OutboxWriter(DataSource source, MessageCodec codec, ApplicationIdentity identity) {
    this.source = source;
    this.jdbc = new JdbcTemplate(source);
    this.codec = codec;
    scope = identity.namespace() + ":" + identity.environment();
  }

  public void append(MessageEnvelope envelope) {
    if (!TransactionSynchronizationManager.isActualTransactionActive()
        || !TransactionSynchronizationManager.hasResource(source))
      throw new IllegalStateException("Outbox append requires a transaction on its DataSource");
    String body = new String(codec.encode(envelope), StandardCharsets.UTF_8);
    jdbc.update(
        "INSERT INTO"
            + " infra_message_outbox(scope,message_id,body,status,attempts,next_attempt,created_at)"
            + " VALUES (?,?,?,'PENDING',0,CURRENT_TIMESTAMP(3),CURRENT_TIMESTAMP(3))",
        scope,
        envelope.messageId().toString(),
        body);
  }
}

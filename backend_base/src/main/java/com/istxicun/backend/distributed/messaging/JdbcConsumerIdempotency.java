package com.istxicun.backend.distributed.messaging;

import com.istxicun.backend.common.identity.ApplicationIdentity;
import java.util.Objects;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/** Dedicated REQUIRES_NEW transaction; businessWork must use this same DataSource. */
public final class JdbcConsumerIdempotency implements ConsumerIdempotency {
  private final JdbcTemplate jdbc;
  private final TransactionTemplate tx;
  private final String scope;

  public JdbcConsumerIdempotency(DataSource source, ApplicationIdentity identity) {
    jdbc = new JdbcTemplate(source);
    tx = new TransactionTemplate(new DataSourceTransactionManager(source));
    tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    scope = identity.namespace() + ":" + identity.environment();
  }

  @Override
  public void execute(String consumer, MessageEnvelope envelope, Runnable action) {
    MessagingNames.consumer(consumer);
    Objects.requireNonNull(action);
    tx.executeWithoutResult(
        status -> {
          // A token distinguishes this transaction's insert from a concurrently committed
          // duplicate.
          String token = java.util.UUID.randomUUID().toString();
          jdbc.update(
              "INSERT INTO"
                  + " infra_message_consumed(scope,consumer_name,message_id,claim_token,processed_at)"
                  + " VALUES (?,?,?,?,CURRENT_TIMESTAMP(3)) ON DUPLICATE KEY UPDATE"
                  + " message_id=message_id",
              scope,
              consumer,
              envelope.messageId().toString(),
              token);
          String owner =
              jdbc.queryForObject(
                  "SELECT claim_token FROM infra_message_consumed WHERE scope=? AND consumer_name=?"
                      + " AND message_id=?",
                  String.class,
                  scope,
                  consumer,
                  envelope.messageId().toString());
          if (token.equals(owner)) action.run();
        });
  }
}

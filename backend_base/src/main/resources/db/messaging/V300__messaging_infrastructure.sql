-- Explicit opt-in location: classpath:db/messaging. Never included by the default Flyway locations.
CREATE TABLE infra_message_consumed (
  scope VARCHAR(129) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  consumer_name VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  message_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  claim_token CHAR(36) CHARACTER SET ascii NOT NULL,
  processed_at DATETIME(3) NOT NULL,
  PRIMARY KEY(scope,consumer_name,message_id), KEY ix_consumed_retention(processed_at)
) ENGINE=InnoDB;
CREATE TABLE infra_message_outbox (
  scope VARCHAR(129) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  message_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  body MEDIUMTEXT NOT NULL,
  status VARCHAR(16) CHARACTER SET ascii NOT NULL,
  attempts INT NOT NULL,
  claim_token CHAR(36) CHARACTER SET ascii,
  lease_until DATETIME(3), next_attempt DATETIME(3) NOT NULL,
  created_at DATETIME(3) NOT NULL, sent_at DATETIME(3),
  PRIMARY KEY(scope,message_id),
  KEY ix_outbox_pending(scope,status,next_attempt,created_at),
  KEY ix_outbox_lease(scope,status,lease_until), KEY ix_outbox_retention(scope,status,sent_at)
) ENGINE=InnoDB;

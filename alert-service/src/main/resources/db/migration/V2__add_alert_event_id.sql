ALTER TABLE alerts
    ADD COLUMN event_id VARCHAR(100) NULL;

CREATE UNIQUE INDEX uq_alerts_event_id ON alerts(event_id);

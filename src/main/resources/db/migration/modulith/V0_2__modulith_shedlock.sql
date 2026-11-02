-- ShedLock table for scheduled jobs (order expiry, cleanups), so a job runs on one instance at a time.
-- The standard ShedLock layout, with timestamptz to follow the UTC rule.
CREATE TABLE public.shedlock
(
  name       VARCHAR(64)  NOT NULL,
  lock_until TIMESTAMPTZ  NOT NULL,
  locked_at  TIMESTAMPTZ  NOT NULL,
  locked_by  VARCHAR(255) NOT NULL,
  PRIMARY KEY (name)
);

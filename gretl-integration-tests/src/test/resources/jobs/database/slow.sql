select id, name from colors, pg_sleep(2) /* cancellation-checkpoint */;

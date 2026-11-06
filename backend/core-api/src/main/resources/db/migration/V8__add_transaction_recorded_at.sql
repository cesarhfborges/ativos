-- timestamp passa a ser a data da operação, que pode ser retroativa; recorded_at é quando ela foi lançada.
ALTER TABLE transactions ADD COLUMN recorded_at TIMESTAMPTZ;
UPDATE transactions SET recorded_at = timestamp;
ALTER TABLE transactions ALTER COLUMN recorded_at SET NOT NULL;

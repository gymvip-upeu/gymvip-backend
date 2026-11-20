-- Explicit, narrowly scoped compatibility step for databases already migrated by Acosta.
-- Back up first. Run with: PGOPTIONS='-c gymvip.allow_e03_history_reconciliation=on' psql ... -v ON_ERROR_STOP=1 -f scripts/reconcile-e03-history.sql
-- Never run a general Flyway repair to bypass unrelated checksum differences.
BEGIN;
DO $$
DECLARE item RECORD; installed INTEGER;
BEGIN
 IF current_setting('gymvip.allow_e03_history_reconciliation',true) IS DISTINCT FROM 'on' THEN
   RAISE EXCEPTION 'Explicit opt-in and backup required: set gymvip.allow_e03_history_reconciliation=on';
 END IF;
 IF NOT EXISTS(SELECT 1 FROM pg_constraint WHERE conname='faq_proposal_complete' AND conrelid='gym_faqs'::regclass)
 OR (SELECT count(*) FROM information_schema.columns WHERE table_schema=current_schema() AND
   ((table_name='gym_faqs' AND column_name IN ('revision','proposal_question','proposal_answer','proposal_category','updated_at_utc')) OR
    (table_name='sent_reminder_dispatches' AND column_name IN ('title','message','template_name','updated_at')) OR
    (table_name='reminder_configurations' AND column_name='post_expiry_days_selected' AND is_nullable='NO'))) <> 10 THEN
   RAISE EXCEPTION 'E03 schema differs from expected migrated structure; no changes applied';
 END IF;
 FOR item IN SELECT * FROM (VALUES ('16',1906511705,-377916174),('17',-1477079838,1889213438)) AS known(version,canonical,acosta) LOOP
   SELECT checksum INTO STRICT installed FROM flyway_schema_history WHERE version=item.version AND success=true;
   IF installed NOT IN (item.canonical,item.acosta) THEN
     RAISE EXCEPTION 'Unknown checksum for V%; stop and review this database',item.version;
   END IF;
   UPDATE flyway_schema_history SET checksum=item.canonical WHERE version=item.version AND checksum=item.acosta AND success=true;
 END LOOP;
END $$;
COMMIT;

ALTER TABLE support_tickets
    ALTER COLUMN clinic_unread DROP DEFAULT,
    ALTER COLUMN clinic_unread TYPE integer USING (CASE WHEN clinic_unread THEN 1 ELSE 0 END),
    ALTER COLUMN clinic_unread SET DEFAULT 0,
    ADD CONSTRAINT support_tickets_clinic_unread_check CHECK (clinic_unread >= 0);

ALTER TABLE support_tickets
    ALTER COLUMN support_unread DROP DEFAULT,
    ALTER COLUMN support_unread TYPE integer USING (CASE WHEN support_unread THEN 1 ELSE 0 END),
    ALTER COLUMN support_unread SET DEFAULT 1,
    ADD CONSTRAINT support_tickets_support_unread_check CHECK (support_unread >= 0);

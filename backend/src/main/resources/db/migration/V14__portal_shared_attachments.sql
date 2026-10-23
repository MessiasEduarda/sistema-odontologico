-- V14: anexos que a clínica libera para o paciente ver e baixar pelo portal. Desligado por padrão: fotos e exames
-- só aparecem no portal quando alguém da clínica marca "Mostrar ao paciente".
ALTER TABLE attachments ADD COLUMN shared_with_patient boolean NOT NULL DEFAULT false;
GRANT UPDATE (shared_with_patient) ON attachments TO ${app_db_user};

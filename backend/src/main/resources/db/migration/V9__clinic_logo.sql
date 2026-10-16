-- V9: logo da clínica (exibida no menu e nos PDFs). Tabela própria para não carregar a imagem junto com as
-- configurações. Formatos aceitos são validados pelo conteúdo na aplicação (PNG, JPG, WEBP; até 1 MB).
CREATE TABLE clinic_logos (
    tenant_id    uuid PRIMARY KEY REFERENCES tenants (id),
    content_type varchar(20) NOT NULL CHECK (content_type IN ('image/png', 'image/jpeg', 'image/webp')),
    data         bytea NOT NULL CHECK (octet_length(data) BETWEEN 1 AND 1048576),
    updated_by   uuid,
    updated_at   timestamptz NOT NULL DEFAULT now()
);
SELECT enable_tenant_rls('clinic_logos');

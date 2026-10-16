-- V10: aceite dos Termos de uso e da Política de privacidade.
-- Global (sem tenant): um registro por usuário e versão dos termos. Imutável para a aplicação — serve de comprovação
-- (data, IP e navegador) e nunca é alterado ou apagado.

CREATE TABLE terms_acceptances (
    user_id     uuid         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    version     varchar(20)  NOT NULL,
    accepted_at timestamptz  NOT NULL DEFAULT now(),
    ip          varchar(64),
    user_agent  varchar(255),
    PRIMARY KEY (user_id, version)
);

REVOKE UPDATE, DELETE ON terms_acceptances FROM ${app_db_user};

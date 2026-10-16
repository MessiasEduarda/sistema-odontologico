-- V13: link de pagamento da assinatura (PIX, boleto ou link de cobrança gerado fora do sistema, sem gateway pago).
-- Com o pagamento vencido, a clínica vê um aviso com o botão "Pagar agora" que abre este link; passada a tolerância,
-- o acesso fica bloqueado até a plataforma registrar o pagamento (situação "Ativa" com novo fim de período).

ALTER TABLE subscriptions ADD COLUMN payment_url varchar(500);

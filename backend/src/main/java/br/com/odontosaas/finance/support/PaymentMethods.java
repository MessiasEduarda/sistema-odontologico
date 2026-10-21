package br.com.odontosaas.finance.support;

/** Formas de pagamento aceitas (usadas na validação dos DTOs de recebimento e de baixa de contas). */
public final class PaymentMethods {

    /** Expressão regular com todas as formas de pagamento válidas. */
    public static final String REGEX = "CASH|PIX|CREDIT_CARD|DEBIT_CARD|BANK_TRANSFER|BOLETO|OTHER";

    private PaymentMethods() {}
}

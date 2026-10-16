/**
 * WhatsApp sem custo: abre o WhatsApp (app ou web) já com o número e a mensagem prontos; a pessoa só aperta enviar.
 * Não usa API paga nem guarda nada no WhatsApp.
 */

/** Número no formato internacional só com dígitos (55 + DDD + número), ou null se não for um celular válido. */
export function whatsappNumber(phone: string | null | undefined): string | null {
  const digits = (phone ?? "").replace(/\D/g, "");
  if (digits.length === 10 || digits.length === 11) return `55${digits}`;
  if ((digits.length === 12 || digits.length === 13) && digits.startsWith("55")) return digits;
  return null;
}

/** Link wa.me com a mensagem preenchida. */
export function whatsappLink(phone: string, message: string): string | null {
  const number = whatsappNumber(phone);
  return number ? `https://wa.me/${number}?text=${encodeURIComponent(message)}` : null;
}

/** Primeiro nome, para mensagens mais próximas ("Olá, Ana!"). */
export function firstName(name: string | null | undefined): string {
  return (name ?? "").trim().split(/\s+/)[0] ?? "";
}

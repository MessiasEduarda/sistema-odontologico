"use client";

import { MessageCircle } from "lucide-react";
import { useState } from "react";
import { Button } from "@/components/ui/Button";
import { useFeedback } from "@/components/ui/Feedback";
import { whatsappNumber } from "@/lib/whatsapp";

export function WhatsAppButton({
  phone,
  allowed,
  message,
  label = "WhatsApp",
  variant = "outline",
}: {
  phone: string | null | undefined;
  allowed: boolean;
  message: () => string | Promise<string>;
  label?: string;
  variant?: "outline" | "ghost" | "primary";
}) {
  const feedback = useFeedback();
  const [pending, setPending] = useState(false);
  const number = whatsappNumber(phone);
  const reason = !number
    ? "Paciente sem celular válido no cadastro."
    : !allowed
      ? "O paciente não autorizou contato por WhatsApp (marque “Aceita WhatsApp” no cadastro)."
      : undefined;

  const open = async () => {
    if (!number) return;
    const tab = window.open("about:blank", "_blank");
    setPending(true);
    try {
      const text = await message();
      const url = `https://wa.me/${number}?text=${encodeURIComponent(text)}`;
      if (tab) tab.location.href = url;
      else window.location.href = url;
    } catch (error) {
      tab?.close();
      feedback.error("Não foi possível preparar a mensagem", error instanceof Error ? error.message : undefined);
    } finally {
      setPending(false);
    }
  };

  return (
    <Button
      variant={variant}
      onClick={() => void open()}
      disabled={Boolean(reason)}
      loading={pending}
      title={reason ?? "Abre o WhatsApp com a mensagem pronta; é só enviar."}
    >
      <MessageCircle size={16} aria-hidden /> {label}
    </Button>
  );
}

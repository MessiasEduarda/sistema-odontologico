/** Formatação no padrão brasileiro. Datas da API chegam em ISO-8601 (UTC) e são exibidas no fuso local. */

const money = new Intl.NumberFormat("pt-BR", { style: "currency", currency: "BRL" });
const dateFmt = new Intl.DateTimeFormat("pt-BR", { day: "2-digit", month: "2-digit", year: "numeric" });
const dateTimeFmt = new Intl.DateTimeFormat("pt-BR", {
  day: "2-digit",
  month: "2-digit",
  year: "numeric",
  hour: "2-digit",
  minute: "2-digit",
});
const timeFmt = new Intl.DateTimeFormat("pt-BR", { hour: "2-digit", minute: "2-digit" });

export const formatMoney = (value: number | string | null | undefined) =>
  value === null || value === undefined || value === "" ? "—" : money.format(Number(value));

/** Datas sem hora ("2026-10-13") são interpretadas como data local, sem deslocamento de fuso. */
export function parseDateOnly(value: string): Date {
  const [y, m, d] = value.split("-").map(Number);
  return new Date(y ?? 1970, (m ?? 1) - 1, d ?? 1);
}

export const formatDate = (value: string | null | undefined) => {
  if (!value) return "—";
  return dateFmt.format(value.length === 10 ? parseDateOnly(value) : new Date(value));
};

export const formatDateTime = (value: string | null | undefined) => (value ? dateTimeFmt.format(new Date(value)) : "—");
export const formatTime = (value: string | null | undefined) => (value ? timeFmt.format(new Date(value)) : "—");

/** "2026-10-13" no fuso local (para inputs type=date). */
export function toDateInput(date: Date): string {
  const y = date.getFullYear();
  const m = String(date.getMonth() + 1).padStart(2, "0");
  const d = String(date.getDate()).padStart(2, "0");
  return `${y}-${m}-${d}`;
}

/** "14:30" no fuso local (para inputs type=time). */
export function toTimeInput(date: Date): string {
  return `${String(date.getHours()).padStart(2, "0")}:${String(date.getMinutes()).padStart(2, "0")}`;
}

/** Junta data e hora locais em ISO-8601 com fuso (o servidor guarda em UTC). */
export function localDateTimeToIso(date: string, time: string): string {
  return new Date(`${date}T${time}:00`).toISOString();
}

export function onlyDigits(value: string | null | undefined): string {
  return (value ?? "").replace(/\D/g, "");
}

export function formatCpf(value: string | null | undefined): string {
  const d = onlyDigits(value);
  if (d.length !== 11) return value ?? "—";
  return `${d.slice(0, 3)}.${d.slice(3, 6)}.${d.slice(6, 9)}-${d.slice(9)}`;
}

export function formatPhone(value: string | null | undefined): string {
  const d = onlyDigits(value);
  if (d.length === 11) return `(${d.slice(0, 2)}) ${d.slice(2, 7)}-${d.slice(7)}`;
  if (d.length === 10) return `(${d.slice(0, 2)}) ${d.slice(2, 6)}-${d.slice(6)}`;
  return value || "—";
}

/** Idade em anos completos a partir de "AAAA-MM-DD". */
export function ageFrom(birthDate: string | null | undefined, today = new Date()): number | null {
  if (!birthDate) return null;
  const b = parseDateOnly(birthDate);
  let age = today.getFullYear() - b.getFullYear();
  const beforeBirthday =
    today.getMonth() < b.getMonth() || (today.getMonth() === b.getMonth() && today.getDate() < b.getDate());
  if (beforeBirthday) age -= 1;
  return age;
}

/** Validação de CPF (dígitos verificadores). */
export function isValidCpf(value: string): boolean {
  const d = onlyDigits(value);
  if (d.length !== 11 || /^(\d)\1{10}$/.test(d)) return false;
  const calc = (len: number) => {
    let sum = 0;
    for (let i = 0; i < len; i++) sum += Number(d[i]) * (len + 1 - i);
    const rest = (sum * 10) % 11;
    return rest === 10 ? 0 : rest;
  };
  return calc(9) === Number(d[9]) && calc(10) === Number(d[10]);
}

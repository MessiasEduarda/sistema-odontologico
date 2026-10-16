import { describe, expect, it } from "vitest";
import type { WaitlistEntry } from "@/lib/api/types";
import { offerMessage } from "./Waitlist";

const entry = {
  patientName: "Ana Maria Souza",
  professionalName: "Dra. Paula",
} as WaitlistEntry;

describe("offerMessage", () => {
  it("oferece o horário com o primeiro nome, a clínica e o profissional", () => {
    const text = offerMessage(entry, "Clínica Sorriso");
    expect(text).toContain("Olá, Ana! Aqui é da Clínica Sorriso.");
    expect(text).toContain("Abriu um horário na agenda com Dra. Paula");
  });

  it("funciona sem profissional definido", () => {
    expect(offerMessage({ ...entry, professionalName: null }, undefined)).toContain(
      "Abriu um horário na agenda e você está na nossa lista de espera.",
    );
  });
});

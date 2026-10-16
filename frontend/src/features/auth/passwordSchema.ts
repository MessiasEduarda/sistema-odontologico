import { z } from "zod";

/** Espelha a política do back-end (PasswordPolicy). O back-end continua sendo a autoridade final. */
export const newPasswordSchema = z
  .string()
  .min(10, { error: "A senha deve ter pelo menos 10 caracteres." })
  .max(128, { error: "A senha deve ter no máximo 128 caracteres." });

export const PASSWORD_HINT = "Use pelo menos 10 caracteres. Frases longas são fáceis de lembrar e seguras.";

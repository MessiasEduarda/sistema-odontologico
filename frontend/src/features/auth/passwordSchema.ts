export const PASSWORD_MIN_LENGTH = 8;

export function passwordProblem(password: string, confirm?: string): string | null {
  if (password.length < PASSWORD_MIN_LENGTH) return `A senha deve ter no mínimo ${PASSWORD_MIN_LENGTH} caracteres.`;
  if (!/\p{Lu}/u.test(password)) return "A senha deve ter pelo menos uma letra maiúscula.";
  if (!/[^\p{L}\p{N}\s]/u.test(password)) return "A senha deve ter pelo menos um caractere especial (ex.: ! @ # $ %).";
  if (confirm !== undefined && confirm !== password) return "As senhas não coincidem.";
  return null;
}

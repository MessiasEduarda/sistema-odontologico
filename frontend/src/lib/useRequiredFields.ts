import { useState } from "react";

type Rules = Record<string, string | false | null | undefined>;

export const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

export function useRequiredFields(rules: Rules) {
  const [submitted, setSubmitted] = useState(false);
  const current: Record<string, string> = {};
  for (const [field, message] of Object.entries(rules)) {
    if (message) current[field] = message;
  }
  return {
    errors: submitted ? current : ({} as Record<string, string>),
    check: () => {
      setSubmitted(true);
      return Object.keys(current).length === 0;
    },
    reset: () => setSubmitted(false),
  };
}

import { describe, expect, it } from "vitest";
import { saveProblemReport, takeProblemReport } from "./support-report";

describe("relato de problema", () => {
  it("guarda o erro para o formulário de suporte e só entrega uma vez", () => {
    saveProblemReport({ traceId: "abc-123", message: "Falhou", page: "/agenda" });
    expect(takeProblemReport()).toEqual({ traceId: "abc-123", message: "Falhou", page: "/agenda" });
    expect(takeProblemReport()).toBeNull();
  });
});

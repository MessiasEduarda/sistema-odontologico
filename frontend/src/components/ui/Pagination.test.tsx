import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { renderWithProviders } from "@/test/render";
import { DataTable } from "./DataTable";
import { pageSlots } from "./Pagination";

describe("pageSlots", () => {
  it("mostra todas as páginas quando são poucas", () => {
    expect(pageSlots(0, 5)).toEqual([0, 1, 2, 3, 4]);
  });

  it("usa reticências como no modelo: [1] 2 3 … 10", () => {
    expect(pageSlots(0, 10)).toEqual([0, 1, 2, 3, "gap", 9]);
    expect(pageSlots(4, 10)).toEqual([0, "gap", 3, 4, 5, "gap", 9]);
    expect(pageSlots(9, 10)).toEqual([0, "gap", 6, 7, 8, 9]);
  });
});

describe("DataTable com paginação", () => {
  const rows = Array.from({ length: 23 }, (_, i) => ({ id: String(i + 1), name: `Pessoa ${i + 1}` }));

  it("mostra 10 por página e navega pelos números e setas", async () => {
    const user = userEvent.setup();
    renderWithProviders(
      <DataTable
        caption="Pessoas"
        rows={rows}
        rowKey={(r) => r.id}
        columns={[{ key: "name", header: "Nome", render: (r) => r.name }]}
      />,
    );

    expect(screen.getByText("Mostrando 10 de 23")).toBeInTheDocument();
    expect(screen.getByText("Pessoa 1")).toBeInTheDocument();
    expect(screen.queryByText("Pessoa 11")).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Página 1" })).toHaveAttribute("aria-current", "page");
    expect(screen.getByRole("button", { name: "Página anterior" })).toBeDisabled();

    await user.click(screen.getByRole("button", { name: "Página 3" }));
    expect(screen.getByText("Mostrando 3 de 23")).toBeInTheDocument();
    expect(screen.getByText("Pessoa 23")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Próxima página" })).toBeDisabled();

    await user.click(screen.getByRole("button", { name: "Página anterior" }));
    expect(screen.getByText("Pessoa 11")).toBeInTheDocument();
  });
});

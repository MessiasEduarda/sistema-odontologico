import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { useState } from "react";
import { describe, expect, it, vi } from "vitest";
import { renderWithProviders } from "@/test/render";
import { SelectField } from "./fields";

const OPTIONS = [
  { value: "PRESCRIPTION", label: "Receituário" },
  { value: "CERTIFICATE", label: "Atestado" },
  { value: "REFERRAL", label: "Encaminhamento" },
];

function Harness({ onChange }: { onChange?: (v: string) => void }) {
  const [value, setValue] = useState("");
  return (
    <SelectField
      label="Tipo"
      placeholder="Todos"
      value={value}
      options={OPTIONS}
      onChange={(e) => {
        setValue(e.target.value);
        onChange?.(e.target.value);
      }}
    />
  );
}

describe("SelectField (lista personalizada)", () => {
  it("abre a lista ao clicar e seleciona com o mouse", async () => {
    const user = userEvent.setup();
    const onChange = vi.fn();
    renderWithProviders(<Harness onChange={onChange} />);

    const combo = screen.getByRole("combobox", { name: "Tipo" });
    expect(combo).toHaveTextContent("Todos");
    expect(combo).toHaveAttribute("aria-expanded", "false");

    await user.click(combo);
    expect(combo).toHaveAttribute("aria-expanded", "true");
    expect(screen.getAllByRole("option")).toHaveLength(4);

    await user.click(screen.getByRole("option", { name: "Atestado" }));
    expect(onChange).toHaveBeenCalledWith("CERTIFICATE");
    expect(combo).toHaveTextContent("Atestado");
    expect(screen.queryByRole("listbox")).not.toBeInTheDocument();
  });

  it("funciona só pelo teclado (setas, Enter, Esc e letra inicial)", async () => {
    const user = userEvent.setup();
    renderWithProviders(<Harness />);
    const combo = screen.getByRole("combobox", { name: "Tipo" });

    combo.focus();
    await user.keyboard("{ArrowDown}");
    expect(screen.getByRole("listbox")).toBeInTheDocument();
    await user.keyboard("{ArrowDown}{Enter}");
    expect(combo).toHaveTextContent("Receituário");

    await user.keyboard("{ArrowDown}{Escape}");
    expect(screen.queryByRole("listbox")).not.toBeInTheDocument();
    expect(combo).toHaveTextContent("Receituário");

    await user.keyboard("e");
    expect(combo).toHaveTextContent("Encaminhamento");
  });

  it("marca a opção escolhida como selecionada", async () => {
    const user = userEvent.setup();
    renderWithProviders(<Harness />);
    const combo = screen.getByRole("combobox", { name: "Tipo" });
    await user.click(combo);
    await user.click(screen.getByRole("option", { name: "Encaminhamento" }));
    await user.click(combo);
    expect(screen.getByRole("option", { name: "Encaminhamento" })).toHaveAttribute("aria-selected", "true");
  });
});

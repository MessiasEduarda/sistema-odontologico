import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { useState } from "react";
import { describe, expect, it, vi } from "vitest";
import { renderWithProviders } from "@/test/render";
import { CheckboxField, ColorField, FileField } from "./fields";

describe("CheckboxField (caixa personalizada)", () => {
  it("marca e desmarca pelo rótulo e pelo teclado", async () => {
    const user = userEvent.setup();
    function Harness() {
      const [checked, setChecked] = useState(false);
      return <CheckboxField label="Dia inteiro" checked={checked} onChange={(e) => setChecked(e.target.checked)} />;
    }
    renderWithProviders(<Harness />);
    const box = screen.getByRole("checkbox", { name: "Dia inteiro" });

    await user.click(screen.getByText("Dia inteiro"));
    expect(box).toBeChecked();

    box.focus();
    await user.keyboard(" ");
    expect(box).not.toBeChecked();
  });
});

describe("FileField", () => {
  it("abre a escolha pelo botão próprio e mostra o nome do arquivo", async () => {
    const user = userEvent.setup();
    const onChange = vi.fn();
    function Harness() {
      const [name, setName] = useState<string | null>(null);
      return (
        <FileField
          label="Arquivo"
          fileName={name}
          onChange={(file) => {
            onChange(file);
            setName(file?.name ?? null);
          }}
        />
      );
    }
    renderWithProviders(<Harness />);
    expect(screen.getByText("Nenhum arquivo escolhido")).toBeInTheDocument();

    const file = new File(["conteúdo"], "radiografia.png", { type: "image/png" });
    await user.upload(screen.getByLabelText("Arquivo"), file);

    expect(onChange).toHaveBeenCalledWith(file);
    expect(screen.getByText("radiografia.png")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /Escolher arquivo/ })).toBeInTheDocument();
  });
});

describe("ColorField", () => {
  it("mostra o código da cor escolhida", () => {
    renderWithProviders(<ColorField label="Cor na agenda" value="#9c7a5b" onChange={() => {}} />);
    expect(screen.getByText("#9C7A5B")).toBeInTheDocument();
    expect(screen.getByLabelText("Cor na agenda")).toHaveValue("#9c7a5b");
  });
});

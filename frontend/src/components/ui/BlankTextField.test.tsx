import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { useState } from "react";
import { describe, expect, it } from "vitest";
import { renderWithProviders } from "@/test/render";
import { BlankTextField } from "./BlankTextField";

function Harness({ initial, onValue }: { initial: string; onValue: (v: string) => void }) {
  const [value, setValue] = useState(initial);
  return (
    <BlankTextField
      label="Conteúdo"
      value={value}
      onChange={(v) => {
        setValue(v);
        onValue(v);
      }}
    />
  );
}

describe("BlankTextField", () => {
  it("troca os traços por espaços de preenchimento e monta o texto com o que foi digitado", async () => {
    const user = userEvent.setup();
    let last = "";
    renderWithProviders(<Harness initial="das ___h___ às ___h___." onValue={(v) => (last = v)} />);

    const blanks = screen.getAllByRole("textbox");
    expect(blanks).toHaveLength(4);
    expect(screen.queryByText(/___/)).not.toBeInTheDocument();

    await user.type(screen.getByLabelText("Espaço 1 de 4"), "14");
    await user.type(screen.getByLabelText("Espaço 2 de 4"), "30");
    expect(last).toBe("das 14h30 às ___h___.");
  });

  it("permite editar o texto completo quando precisa mudar outra parte", async () => {
    const user = userEvent.setup();
    renderWithProviders(<Harness initial="Uso ______:" onValue={() => {}} />);
    await user.click(screen.getByRole("button", { name: "Editar texto completo" }));
    expect(screen.getByLabelText("Conteúdo")).toHaveValue("Uso ______:");
  });
});

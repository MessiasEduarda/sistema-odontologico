import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { useState } from "react";
import { describe, expect, it, vi } from "vitest";
import { renderWithProviders } from "@/test/render";
import { Button } from "./Button";
import { useFeedback } from "./Feedback";
import { Modal } from "./Modal";
import { TextField } from "./TextField";

function Buttons({ onAnswer }: { onAnswer: (ok: boolean) => void }) {
  const feedback = useFeedback();
  return (
    <>
      <Button onClick={() => feedback.success("Paciente cadastrado com sucesso!")}>sucesso</Button>
      <Button onClick={() => feedback.error("Não foi possível concluir a ação", "Horário ocupado.")}>erro</Button>
      <Button
        onClick={async () =>
          onAnswer(await feedback.confirm({ message: "Tem certeza que deseja inativar este paciente?" }))
        }
      >
        confirmar
      </Button>
    </>
  );
}

function FormInModal({ onClose }: { onClose: () => void }) {
  const [open, setOpen] = useState(true);
  const close = () => {
    setOpen(false);
    onClose();
  };
  return (
    <Modal
      open={open}
      onClose={close}
      title="Novo paciente"
      footer={
        <Button data-modal-cancel variant="outline" onClick={close}>
          Cancelar
        </Button>
      }
    >
      <TextField label="Nome" />
    </Modal>
  );
}

describe("Feedback", () => {
  it("mostra sucesso e erro no modelo do sistema", async () => {
    const user = userEvent.setup();
    renderWithProviders(<Buttons onAnswer={vi.fn()} />);

    await user.click(screen.getByRole("button", { name: "sucesso" }));
    expect(screen.getByRole("heading", { name: "Sucesso!" })).toBeInTheDocument();
    expect(screen.getByText("Paciente cadastrado com sucesso!")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Voltar" }));
    expect(screen.queryByRole("heading", { name: "Sucesso!" })).not.toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "erro" }));
    expect(screen.getByRole("heading", { name: "Erro!" })).toBeInTheDocument();
    expect(screen.getByText("Horário ocupado.")).toBeInTheDocument();
  });

  it("confirmação resolve conforme o botão", async () => {
    const user = userEvent.setup();
    const onAnswer = vi.fn();
    renderWithProviders(<Buttons onAnswer={onAnswer} />);

    await user.click(screen.getByRole("button", { name: "confirmar" }));
    expect(screen.getByRole("heading", { name: "Atenção!" })).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Voltar" }));
    await waitFor(() => expect(onAnswer).toHaveBeenLastCalledWith(false));

    await user.click(screen.getByRole("button", { name: "confirmar" }));
    await user.click(screen.getByRole("button", { name: "Continuar" }));
    await waitFor(() => expect(onAnswer).toHaveBeenLastCalledWith(true));
  });

  it("formulário vazio fecha direto; com dados pede confirmação antes de descartar", async () => {
    const user = userEvent.setup();
    const onClose = vi.fn();
    const { unmount } = renderWithProviders(<FormInModal onClose={onClose} />);
    await user.click(screen.getByRole("button", { name: "Cancelar" }));
    expect(onClose).toHaveBeenCalledTimes(1);
    expect(screen.queryByRole("heading", { name: "Atenção!" })).not.toBeInTheDocument();
    unmount();

    const onClose2 = vi.fn();
    renderWithProviders(<FormInModal onClose={onClose2} />);
    await user.type(screen.getByLabelText("Nome"), "Ana");
    await user.click(screen.getByRole("button", { name: "Cancelar" }));
    expect(screen.getByRole("heading", { name: "Atenção!" })).toBeInTheDocument();
    expect(onClose2).not.toHaveBeenCalled();

    await user.click(screen.getByRole("button", { name: "Voltar" }));
    expect(onClose2).not.toHaveBeenCalled();
    expect(screen.getByLabelText("Nome")).toHaveValue("Ana");

    await user.click(screen.getByRole("button", { name: "Cancelar" }));
    await user.click(screen.getByRole("button", { name: "Continuar" }));
    await waitFor(() => expect(onClose2).toHaveBeenCalledTimes(1));
  });
});

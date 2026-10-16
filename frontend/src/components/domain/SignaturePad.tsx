"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import styled from "styled-components";
import { Button } from "@/components/ui/Button";

interface SignaturePadProps {
  onChange: (dataUrl: string | null) => void;
  label?: string;
}

/**
 * Área para assinatura desenhada com dedo, caneta ou mouse (Pointer Events). Gera PNG em data URL.
 * Alternativa acessível: quem não consegue desenhar pode assinar digitando o nome (gera imagem do nome).
 */
export function SignaturePad({ onChange, label = "Assinatura" }: SignaturePadProps) {
  const canvas = useRef<HTMLCanvasElement>(null);
  const drawing = useRef(false);
  const [empty, setEmpty] = useState(true);
  const [typed, setTyped] = useState("");

  const ctx = () => canvas.current?.getContext("2d") ?? null;

  const resize = useCallback(() => {
    const c = canvas.current;
    if (!c) return;
    const ratio = window.devicePixelRatio || 1;
    const { width, height } = c.getBoundingClientRect();
    c.width = width * ratio;
    c.height = height * ratio;
    const context = c.getContext("2d");
    if (context) {
      context.scale(ratio, ratio);
      context.lineWidth = 2.2;
      context.lineCap = "round";
      context.lineJoin = "round";
      context.strokeStyle = "#1E1C1A";
    }
  }, []);

  useEffect(() => {
    resize();
  }, [resize]);

  const point = (e: React.PointerEvent<HTMLCanvasElement>) => {
    const r = e.currentTarget.getBoundingClientRect();
    return { x: e.clientX - r.left, y: e.clientY - r.top };
  };

  const finish = () => {
    if (!drawing.current) return;
    drawing.current = false;
    onChange(canvas.current?.toDataURL("image/png") ?? null);
  };

  const clear = () => {
    const c = canvas.current;
    const context = ctx();
    if (c && context) context.clearRect(0, 0, c.width, c.height);
    setEmpty(true);
    setTyped("");
    onChange(null);
  };

  const signTyped = () => {
    const c = canvas.current;
    const context = ctx();
    if (!c || !context || !typed.trim()) return;
    context.clearRect(0, 0, c.width, c.height);
    context.font = "italic 32px Georgia, serif";
    context.fillStyle = "#1E1C1A";
    context.fillText(typed.trim(), 16, c.getBoundingClientRect().height / 2 + 10);
    setEmpty(false);
    onChange(c.toDataURL("image/png"));
  };

  return (
    <Wrapper>
      <Label id="signature-label">{label}</Label>
      <Canvas
        ref={canvas}
        role="img"
        aria-labelledby="signature-label"
        onPointerDown={(e) => {
          e.currentTarget.setPointerCapture(e.pointerId);
          const context = ctx();
          if (!context) return;
          drawing.current = true;
          const p = point(e);
          context.beginPath();
          context.moveTo(p.x, p.y);
          setEmpty(false);
        }}
        onPointerMove={(e) => {
          if (!drawing.current) return;
          const context = ctx();
          if (!context) return;
          const p = point(e);
          context.lineTo(p.x, p.y);
          context.stroke();
        }}
        onPointerUp={finish}
        onPointerLeave={finish}
      />
      <Hint>{empty ? "Assine no quadro acima com o dedo, caneta ou mouse." : "Assinatura registrada."}</Hint>
      <Row>
        <TypedInput
          aria-label="Ou digite o nome para gerar a assinatura"
          placeholder="Ou digite seu nome"
          value={typed}
          onChange={(e) => setTyped(e.target.value)}
        />
        <Button variant="outline" type="button" onClick={signTyped} disabled={!typed.trim()}>
          Usar nome digitado
        </Button>
        <Button variant="ghost" type="button" onClick={clear}>
          Limpar
        </Button>
      </Row>
    </Wrapper>
  );
}

const Wrapper = styled.div`
  display: grid;
  gap: ${({ theme }) => theme.space[2]};
`;

const Label = styled.span`
  font-weight: ${({ theme }) => theme.fontWeights.semibold};
`;

const Canvas = styled.canvas`
  width: 100%;
  height: 160px;
  border: 1px dashed ${({ theme }) => theme.colors.borderStrong};
  border-radius: ${({ theme }) => theme.radii.md};
  background: #fff;
  touch-action: none;
  cursor: crosshair;
`;

const Hint = styled.p`
  font-size: ${({ theme }) => theme.fontSizes.sm};
  color: ${({ theme }) => theme.colors.textMuted};
`;

const Row = styled.div`
  display: flex;
  flex-wrap: wrap;
  gap: ${({ theme }) => theme.space[2]};
`;

const TypedInput = styled.input`
  flex: 1;
  min-width: 180px;
  min-height: 42px;
  padding: 0 ${({ theme }) => theme.space[4]};
  border: 1px solid ${({ theme }) => theme.colors.border};
  border-radius: ${({ theme }) => theme.radii.pill};
`;

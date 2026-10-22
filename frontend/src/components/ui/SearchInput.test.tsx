import { act, renderHook } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { useRowSearch } from "./SearchInput";

const rows = [
  { id: "1", patient: "José Antônio", professional: "Dra. Lívia" },
  { id: "2", patient: "Maria Clara", professional: "Dr. Bruno" },
  { id: "3", patient: "Ana Souza", professional: null },
];

describe("useRowSearch", () => {
  it("mostra tudo sem termo e filtra por nome ignorando acentos e maiúsculas", () => {
    const { result } = renderHook(() => useRowSearch(rows, (r) => [r.patient, r.professional]));
    expect(result.current.rows).toHaveLength(3);

    act(() => result.current.setQuery("jose"));
    expect(result.current.rows?.map((r) => r.id)).toEqual(["1"]);

    act(() => result.current.setQuery("  LIVIA "));
    expect(result.current.rows?.map((r) => r.id)).toEqual(["1"]);

    act(() => result.current.setQuery("dr. b"));
    expect(result.current.rows?.map((r) => r.id)).toEqual(["2"]);

    act(() => result.current.setQuery("ninguém"));
    expect(result.current.rows).toEqual([]);
  });

  it("aceita lista ainda não carregada", () => {
    const { result } = renderHook(() => useRowSearch<(typeof rows)[number]>(undefined, (r) => [r.patient]));
    act(() => result.current.setQuery("ana"));
    expect(result.current.rows).toBeUndefined();
  });
});

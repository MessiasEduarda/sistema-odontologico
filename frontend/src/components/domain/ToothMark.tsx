import Image from "next/image";
import toothMenu from "@/assets/tooth-menu.png";

export const TOOTH_PATH =
  "M80 30C64 18 40 20 32 40C24 60 30 80 38 98C44 112 44 140 52 158C58 170 70 166 72 150C74 136 76 124 80 124" +
  "C84 124 86 136 88 150C90 166 102 170 108 158C116 140 116 112 122 98C130 80 136 60 128 40C120 20 96 18 80 30Z";

export function ToothMark({ size = 44 }: { size?: number }) {
  return (
    <Image
      src={toothMenu}
      alt=""
      aria-hidden
      style={{ width: size, height: "auto", flexShrink: 0, display: "block" }}
    />
  );
}

import { describe, expect, it } from "vitest";
import {
  STAGE_ACCENT_BORDER_CLASSES,
  STAGE_BADGE_CLASSES,
  STAGE_DOT_CLASSES,
  STAGE_DROP_HIGHLIGHT_CLASSES,
  STAGE_LABELS,
  STAGES,
} from "@/shared/lib/stages";

const CLASS_MAPS = {
  STAGE_BADGE_CLASSES,
  STAGE_DOT_CLASSES,
  STAGE_ACCENT_BORDER_CLASSES,
  STAGE_DROP_HIGHLIGHT_CLASSES,
};

describe("stages", () => {
  it("lists the stages in the order of the backend enum", () => {
    expect(STAGES).toEqual([
      "APPLIED",
      "INTERVIEW",
      "OFFER",
      "HIRED",
      "REJECTED",
    ]);
  });

  it("has a distinct Spanish label for every stage", () => {
    expect(STAGES.map((stage) => STAGE_LABELS[stage])).toEqual([
      "Postulado",
      "Entrevista",
      "Oferta",
      "Contratado",
      "Rechazado",
    ]);
    expect(Object.keys(STAGE_LABELS)).toHaveLength(STAGES.length);
  });

  it.each(Object.entries(CLASS_MAPS))(
    "%s covers every stage with different classes",
    (_name, classes) => {
      expect(Object.keys(classes).sort()).toEqual([...STAGES].sort());
      const values = STAGES.map((stage) => classes[stage]);
      expect(new Set(values).size).toBe(STAGES.length);
    },
  );

  it.each(STAGES)("uses the %s tokens in every class map", (stage) => {
    const token = `stage-${stage.toLowerCase()}`;
    expect(STAGE_BADGE_CLASSES[stage].split(" ")).toEqual([
      `bg-${token}-soft`,
      `text-${token}-foreground`,
      `border-${token}`,
    ]);
    expect(STAGE_DOT_CLASSES[stage]).toBe(`bg-${token}`);
    expect(STAGE_ACCENT_BORDER_CLASSES[stage]).toBe(`border-t-${token}`);
    expect(STAGE_DROP_HIGHLIGHT_CLASSES[stage].split(" ")).toEqual([
      `bg-${token}-soft`,
      "ring-2",
      `ring-${token}`,
    ]);
  });
});

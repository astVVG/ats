import type { Rating } from "@/features/board/types";

/** "3 de 5 estrellas", or "Sin calificación". */
export function describeRating(value: Rating) {
  return value === null ? "Sin calificación" : `${value} de 5 estrellas`;
}

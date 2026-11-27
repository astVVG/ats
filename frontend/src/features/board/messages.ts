import { isAxiosError } from "axios";

export const HIRED_ADMIN_ONLY =
  "Solo un administrador puede sacar una postulación de Contratado.";

export function httpStatus(error: unknown): number | undefined {
  return isAxiosError(error) ? error.response?.status : undefined;
}

export function moveErrorMessage(error: unknown): string {
  switch (httpStatus(error)) {
    case 409:
      return "La vacante está cerrada y no admite cambios.";
    case 403:
      return HIRED_ADMIN_ONLY;
    case 404:
      return "La postulación ya no existe.";
    default:
      return "No se pudo mover la postulación. Inténtalo de nuevo.";
  }
}

export function createApplicationErrorMessage(error: unknown): string {
  switch (httpStatus(error)) {
    case 409:
      return "No se pudo agregar al candidato: ya está postulado a esta vacante o la vacante está cerrada.";
    case 404:
      return "El candidato o la vacante ya no existe.";
    default:
      return "No se pudo agregar al candidato. Inténtalo de nuevo.";
  }
}

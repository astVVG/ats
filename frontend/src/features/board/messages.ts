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

export const NOTE_INVALID =
  "Revisa la nota: no puede estar vacía ni pasar de 2000 caracteres.";

/** Shown above the form; a 400 goes to the field instead. */
export function createNoteErrorMessage(error: unknown): string {
  return httpStatus(error) === 404
    ? "La postulación ya no existe."
    : "No se pudo guardar la nota. Inténtalo de nuevo.";
}

export function deleteNoteErrorMessage(error: unknown): string {
  switch (httpStatus(error)) {
    case 403:
      return "No tienes permiso para eliminar esta nota.";
    case 404:
      return "La nota ya no existe.";
    default:
      return "No se pudo eliminar la nota. Inténtalo de nuevo.";
  }
}

export function rateErrorMessage(error: unknown): string {
  return httpStatus(error) === 404
    ? "La postulación ya no existe."
    : "No se pudo guardar la calificación. Inténtalo de nuevo.";
}

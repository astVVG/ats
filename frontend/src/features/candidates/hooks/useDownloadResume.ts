import { useMutation } from "@tanstack/react-query";
import { downloadResume } from "@/features/candidates/api";

function saveBlob(blob: Blob, fileName: string) {
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = fileName;
  document.body.append(link);
  link.click();
  link.remove();
  URL.revokeObjectURL(url);
}

/** Fetches the resume with the session token and saves it as cv-{id}.pdf. */
export function useDownloadResume(id: number) {
  return useMutation({
    mutationFn: async () => {
      const blob = await downloadResume(id);
      saveBlob(blob, `cv-${id}.pdf`);
    },
  });
}

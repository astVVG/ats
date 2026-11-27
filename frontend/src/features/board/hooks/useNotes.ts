import {
  keepPreviousData,
  useMutation,
  useQuery,
  useQueryClient,
} from "@tanstack/react-query";
import { createNote, deleteNote, listNotes } from "@/features/board/api";
import { httpStatus } from "@/features/board/messages";

/** Every page of an application's notes. */
export function applicationNotesKey(applicationId: number) {
  return ["applications", applicationId, "notes"] as const;
}

export function notesQueryKey(applicationId: number, page: number) {
  return [...applicationNotesKey(applicationId), page] as const;
}

export function useNotes(applicationId: number, page: number) {
  return useQuery({
    queryKey: notesQueryKey(applicationId, page),
    queryFn: () => listNotes(applicationId, page),
    // Keeps the current page on screen while the next one loads.
    placeholderData: keepPreviousData,
  });
}

export function useCreateNote(applicationId: number) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (content: string) => createNote(applicationId, { content }),
    onSuccess: () =>
      queryClient.invalidateQueries({
        queryKey: applicationNotesKey(applicationId),
      }),
  });
}

/** Reloads the notes after a deletion, and also when the note was already gone. */
export function useDeleteNote(applicationId: number) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (noteId: number) => deleteNote(noteId),
    onSettled: (_data, error) => {
      if (!error || httpStatus(error) === 404) {
        return queryClient.invalidateQueries({
          queryKey: applicationNotesKey(applicationId),
        });
      }
    },
  });
}

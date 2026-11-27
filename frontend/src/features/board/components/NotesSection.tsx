import { zodResolver } from "@hookform/resolvers/zod";
import { Trash2 } from "lucide-react";
import { useState } from "react";
import { useForm, useWatch } from "react-hook-form";
import { toast } from "sonner";
import { z } from "zod";
import { useAuth } from "@/features/auth/hooks/useAuth";
import {
  SectionEmpty,
  SectionError,
  SectionLoading,
} from "@/features/board/components/DetailStates";
import {
  useCreateNote,
  useDeleteNote,
  useNotes,
} from "@/features/board/hooks/useNotes";
import {
  createNoteErrorMessage,
  deleteNoteErrorMessage,
  httpStatus,
  NOTE_INVALID,
} from "@/features/board/messages";
import { MAX_NOTE_LENGTH, type Note } from "@/features/board/types";
import { Pagination } from "@/shared/components/Pagination";
import { cn } from "@/shared/lib/utils";
import { Alert, AlertDescription } from "@/shared/ui/alert";
import { Button } from "@/shared/ui/button";
import {
  Dialog,
  DialogClose,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/shared/ui/dialog";
import { Label } from "@/shared/ui/label";
import { Textarea } from "@/shared/ui/textarea";

const dateTimeFormat = new Intl.DateTimeFormat("es-MX", {
  dateStyle: "medium",
  timeStyle: "short",
});

const noteSchema = z.object({
  content: z
    .string()
    .trim()
    .min(1, "Escribe la nota")
    .max(
      MAX_NOTE_LENGTH,
      `La nota admite como máximo ${MAX_NOTE_LENGTH} caracteres`,
    ),
});

type NoteValues = z.infer<typeof noteSchema>;

type NoteFormProps = {
  applicationId: number;
  /** Called once the note is saved. */
  onCreated: () => void;
};

function NoteForm({ applicationId, onCreated }: NoteFormProps) {
  const create = useCreateNote(applicationId);
  const [formError, setFormError] = useState<string | null>(null);
  const {
    register,
    handleSubmit,
    setError,
    reset,
    control,
    formState: { errors, isSubmitting },
  } = useForm<NoteValues>({
    resolver: zodResolver(noteSchema),
    defaultValues: { content: "" },
  });
  const length = useWatch({ control, name: "content" }).trim().length;
  const tooLong = length > MAX_NOTE_LENGTH;

  const submit = handleSubmit(async ({ content }) => {
    setFormError(null);
    try {
      await create.mutateAsync(content);
      reset({ content: "" });
      onCreated();
    } catch (error) {
      // The text stays in the field so nothing is lost.
      if (httpStatus(error) === 400) {
        setError("content", { message: NOTE_INVALID });
      } else {
        setFormError(createNoteErrorMessage(error));
      }
    }
  });

  const pending = isSubmitting || create.isPending;
  const describedBy = cn(
    "note-content-count",
    errors.content && "note-content-error",
  );

  return (
    <form className="space-y-2" onSubmit={submit} noValidate>
      {formError && (
        <Alert variant="destructive">
          <AlertDescription>{formError}</AlertDescription>
        </Alert>
      )}
      <Label htmlFor="note-content">Nueva nota</Label>
      <Textarea
        id="note-content"
        rows={3}
        aria-invalid={errors.content ? true : undefined}
        aria-describedby={describedBy}
        {...register("content")}
      />
      <div className="flex items-start justify-between gap-2">
        <div className="min-w-0">
          {errors.content?.message && (
            <p id="note-content-error" className="text-sm text-destructive">
              {errors.content.message}
            </p>
          )}
        </div>
        <span
          id="note-content-count"
          className={cn(
            "shrink-0 text-xs text-muted-foreground tabular-nums",
            tooLong && "text-destructive",
          )}
        >
          {length}/{MAX_NOTE_LENGTH}
          <span className="sr-only"> caracteres</span>
        </span>
      </div>
      <Button type="submit" size="sm" disabled={pending}>
        {pending ? "Guardando…" : "Agregar nota"}
      </Button>
    </form>
  );
}

type NoteItemProps = {
  note: Note;
  canDelete: boolean;
  onDelete: (note: Note) => void;
};

function NoteItem({ note, canDelete, onDelete }: NoteItemProps) {
  return (
    <li className="space-y-1 rounded-lg border bg-card p-3 text-card-foreground">
      <div className="flex items-start justify-between gap-2">
        <p className="min-w-0 text-sm">
          <span className="font-medium">{note.author.name}</span>
          <span className="text-muted-foreground">
            {" · "}
            <time dateTime={note.createdAt}>
              {dateTimeFormat.format(new Date(note.createdAt))}
            </time>
          </span>
        </p>
        {canDelete && (
          <Button
            type="button"
            variant="ghost"
            size="icon-xs"
            className="text-muted-foreground"
            aria-label={`Eliminar nota de ${note.author.name}`}
            onClick={() => onDelete(note)}
          >
            <Trash2 aria-hidden="true" />
          </Button>
        )}
      </div>
      {/* Plain text: React escapes it, and pre-wrap keeps the line breaks. */}
      <p className="text-sm break-words whitespace-pre-wrap">{note.content}</p>
    </li>
  );
}

export function NotesSection({ applicationId }: { applicationId: number }) {
  const { user } = useAuth();
  const [page, setPage] = useState(0);
  const [pendingDelete, setPendingDelete] = useState<Note | null>(null);
  const query = useNotes(applicationId, page);
  const remove = useDeleteNote(applicationId);

  const data = query.data;
  if (
    data &&
    !query.isPlaceholderData &&
    data.content.length === 0 &&
    page > 0
  ) {
    // The page emptied (its last note was deleted elsewhere): show the last one left.
    setPage(Math.max(0, Math.min(page - 1, data.totalPages - 1)));
  }

  const canDelete = (note: Note) =>
    user !== null && (user.role === "ADMIN" || note.author.id === user.id);

  function confirmDelete() {
    if (!pendingDelete) {
      return;
    }
    const onlyOnPage = (data?.content.length ?? 0) === 1;
    remove.mutate(pendingDelete.id, {
      onSuccess: () => {
        toast.success("Nota eliminada.");
        if (onlyOnPage && page > 0) {
          setPage(page - 1);
        }
      },
      onError: (error) => toast.error(deleteNoteErrorMessage(error)),
      onSettled: () => setPendingDelete(null),
    });
  }

  return (
    <section aria-labelledby="detail-notes-heading" className="space-y-3">
      <h3 id="detail-notes-heading" className="font-medium">
        Notas
      </h3>
      <NoteForm applicationId={applicationId} onCreated={() => setPage(0)} />

      {query.isPending && <SectionLoading>Cargando notas…</SectionLoading>}
      {query.isError && (
        <SectionError
          message="No se pudieron cargar las notas."
          retrying={query.isFetching}
          onRetry={() => void query.refetch()}
        />
      )}
      {data && data.content.length === 0 && page === 0 && (
        <SectionEmpty>Todavía no hay notas.</SectionEmpty>
      )}
      {data && data.content.length > 0 && (
        <>
          <ul
            aria-label="Notas de la postulación"
            aria-busy={query.isPlaceholderData || undefined}
            className="space-y-2"
          >
            {data.content.map((note) => (
              <NoteItem
                key={note.id}
                note={note}
                canDelete={canDelete(note)}
                onDelete={setPendingDelete}
              />
            ))}
          </ul>
          {data.totalPages > 1 && (
            <Pagination
              page={page}
              totalPages={data.totalPages}
              onPageChange={setPage}
            />
          )}
        </>
      )}

      <Dialog
        open={pendingDelete !== null}
        onOpenChange={(open) => {
          if (!open && !remove.isPending) {
            setPendingDelete(null);
          }
        }}
      >
        <DialogContent showCloseButton={false}>
          <DialogHeader>
            <DialogTitle>¿Eliminar la nota?</DialogTitle>
            <DialogDescription>
              Esta acción no se puede deshacer.
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <DialogClose asChild>
              <Button
                type="button"
                variant="outline"
                disabled={remove.isPending}
              >
                Cancelar
              </Button>
            </DialogClose>
            <Button
              type="button"
              variant="destructive"
              disabled={remove.isPending}
              onClick={confirmDelete}
            >
              {remove.isPending ? "Eliminando…" : "Eliminar"}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </section>
  );
}

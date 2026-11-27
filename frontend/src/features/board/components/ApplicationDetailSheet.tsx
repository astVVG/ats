import type { ReactNode } from "react";
import { Link } from "react-router";
import type { LocatedCard } from "@/features/board/board-cache";
import {
  SectionError,
  SectionLoading,
} from "@/features/board/components/DetailStates";
import { HistorySection } from "@/features/board/components/HistorySection";
import { NotesSection } from "@/features/board/components/NotesSection";
import type { BoardCard } from "@/features/board/types";
import { useCandidate } from "@/features/candidates/hooks/useCandidate";
import { StarRating } from "@/features/board/components/StarRating";
import { useRateApplication } from "@/features/board/hooks/useRateApplication";
import { StageBadge } from "@/shared/components/StageBadge";
import type { Stage } from "@/shared/lib/stages";
import { Button } from "@/shared/ui/button";
import {
  Sheet,
  SheetContent,
  SheetDescription,
  SheetHeader,
  SheetTitle,
} from "@/shared/ui/sheet";

function DetailItem({ term, children }: { term: string; children: ReactNode }) {
  return (
    <div className="grid grid-cols-[7rem_1fr] gap-2">
      <dt className="text-muted-foreground">{term}</dt>
      <dd className="min-w-0 break-words">{children}</dd>
    </div>
  );
}

/** Card data is always shown; the rest of the profile loads on its own and may fail alone. */
function CandidateDetails({ card, stage }: { card: BoardCard; stage: Stage }) {
  const query = useCandidate(card.candidate.id);

  return (
    <section aria-labelledby="detail-candidate-heading" className="space-y-3">
      <h3 id="detail-candidate-heading" className="font-medium">
        Candidato
      </h3>
      <dl className="space-y-2 text-sm">
        <DetailItem term="Correo">{card.candidate.email}</DetailItem>
        <DetailItem term="Etapa">
          <StageBadge stage={stage} />
        </DetailItem>
        {query.isSuccess && (
          <>
            <DetailItem term="Teléfono">
              {query.data.phone ?? "Sin teléfono"}
            </DetailItem>
            <DetailItem term="Origen">
              {query.data.source ?? "Sin origen"}
            </DetailItem>
            <DetailItem term="CV">
              {query.data.hasResume ? "Tiene CV" : "Sin CV"}
            </DetailItem>
          </>
        )}
      </dl>
      {query.isPending && (
        <SectionLoading>Cargando datos del candidato…</SectionLoading>
      )}
      {query.isError && (
        <SectionError
          message="No se pudieron cargar los datos del candidato."
          retrying={query.isFetching}
          onRetry={() => void query.refetch()}
        />
      )}
      <Button asChild variant="link" className="h-auto px-0">
        <Link to={`/candidates/${card.candidate.id}`}>Ver perfil completo</Link>
      </Button>
    </section>
  );
}

type ApplicationDetailSheetProps = {
  jobId: number;
  /** The application shown, read from the cached board; null closes the panel. */
  selected: LocatedCard | null;
  onClose: () => void;
  /** Element that gets the focus back once the panel has closed. */
  returnFocusTo: () => HTMLElement | null;
};

function RatingSection({ jobId, card }: { jobId: number; card: BoardCard }) {
  const rate = useRateApplication(jobId, card.id);
  return (
    <section aria-labelledby="detail-rating-heading" className="space-y-2">
      <h3 id="detail-rating-heading" className="font-medium">
        Calificación
      </h3>
      <StarRating
        size="md"
        value={card.rating ?? null}
        label={`Calificación de ${card.candidate.fullName}`}
        onChange={(rating) => rate.mutate(rating)}
      />
    </section>
  );
}

export function ApplicationDetailSheet({
  jobId,
  selected,
  onClose,
  returnFocusTo,
}: ApplicationDetailSheetProps) {
  return (
    <Sheet
      open={selected !== null}
      onOpenChange={(open) => {
        if (!open) {
          onClose();
        }
      }}
    >
      {selected && (
        <SheetContent
          side="right"
          className="gap-0 data-[side=right]:w-full data-[side=right]:sm:max-w-lg"
          onCloseAutoFocus={(event) => {
            // Opened from a card button rather than a SheetTrigger.
            event.preventDefault();
            returnFocusTo()?.focus();
          }}
        >
          <SheetHeader className="border-b pr-12">
            <SheetTitle>
              Postulación de {selected.card.candidate.fullName}
            </SheetTitle>
            <SheetDescription>
              Datos del candidato, calificación, notas e historial.
            </SheetDescription>
          </SheetHeader>
          {/* Keyed so drafts and pages never carry over to another application. */}
          <div
            key={selected.card.id}
            className="flex-1 space-y-8 overflow-y-auto p-4"
          >
            <CandidateDetails card={selected.card} stage={selected.stage} />
            <RatingSection jobId={jobId} card={selected.card} />
            <NotesSection applicationId={selected.card.id} />
            <HistorySection applicationId={selected.card.id} />
          </div>
        </SheetContent>
      )}
    </Sheet>
  );
}

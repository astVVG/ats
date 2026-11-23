import { isAxiosError } from "axios";
import { Link, useParams } from "react-router";
import { CandidateNotFound } from "@/features/candidates/components/CandidateNotFound";
import { ResumeSection } from "@/features/candidates/components/ResumeSection";
import { useCandidate } from "@/features/candidates/hooks/useCandidate";
import { parseCandidateId } from "@/features/candidates/types";
import { Alert, AlertDescription } from "@/shared/ui/alert";
import { Button } from "@/shared/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/shared/ui/card";

const dateFormat = new Intl.DateTimeFormat("es-MX", { dateStyle: "medium" });

function CandidateDetail({ id }: { id: number }) {
  const candidate = useCandidate(id);

  if (candidate.isPending) {
    return (
      <p role="status" className="text-muted-foreground">
        Cargando candidato…
      </p>
    );
  }

  if (candidate.isError) {
    if (
      isAxiosError(candidate.error) &&
      candidate.error.response?.status === 404
    ) {
      return <CandidateNotFound />;
    }
    return (
      <Alert variant="destructive">
        <AlertDescription className="flex items-center justify-between gap-4">
          <span>No se pudo cargar el candidato.</span>
          <Button
            type="button"
            variant="outline"
            size="sm"
            disabled={candidate.isFetching}
            onClick={() => void candidate.refetch()}
          >
            Reintentar
          </Button>
        </AlertDescription>
      </Alert>
    );
  }

  const data = candidate.data;
  const details = [
    { label: "Correo", value: data.email },
    { label: "Teléfono", value: data.phone ?? "—" },
    { label: "Origen", value: data.source ?? "—" },
    {
      label: "Fecha de alta",
      value: dateFormat.format(new Date(data.createdAt)),
    },
  ];

  return (
    <section className="space-y-6">
      <div className="flex flex-wrap items-center justify-between gap-4">
        <h1 className="text-3xl font-bold">{data.fullName}</h1>
        <div className="flex gap-2">
          <Button asChild variant="outline">
            <Link to="/candidates">Volver</Link>
          </Button>
          <Button asChild>
            <Link to={`/candidates/${id}/edit`}>Editar</Link>
          </Button>
        </div>
      </div>

      <dl className="grid gap-4 sm:grid-cols-2">
        {details.map(({ label, value }) => (
          <div key={label}>
            <dt className="text-sm text-muted-foreground">{label}</dt>
            <dd>{value}</dd>
          </div>
        ))}
      </dl>

      <Card>
        <CardHeader>
          <CardTitle>
            <h2>CV</h2>
          </CardTitle>
        </CardHeader>
        <CardContent>
          <ResumeSection candidate={data} />
        </CardContent>
      </Card>
    </section>
  );
}

export function CandidateDetailPage() {
  const id = parseCandidateId(useParams().id);
  return id === null ? (
    <CandidateNotFound />
  ) : (
    <CandidateDetail key={id} id={id} />
  );
}

import { Link } from "react-router";
import { Button } from "@/shared/ui/button";

export function CandidateNotFound() {
  return (
    <section className="space-y-4">
      <h1 className="text-3xl font-bold">Candidato no encontrado</h1>
      <p className="text-muted-foreground">
        El candidato que buscas no existe.
      </p>
      <Button asChild variant="outline">
        <Link to="/candidates">Volver a candidatos</Link>
      </Button>
    </section>
  );
}

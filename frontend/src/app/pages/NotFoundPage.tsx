import { Link } from "react-router";
import { Button } from "@/shared/ui/button";

export function NotFoundPage() {
  return (
    <section className="space-y-4">
      <h1 className="text-3xl font-bold">Página no encontrada</h1>
      <p className="text-muted-foreground">La página que buscas no existe.</p>
      <Button asChild variant="outline">
        <Link to="/">Volver al inicio</Link>
      </Button>
    </section>
  );
}

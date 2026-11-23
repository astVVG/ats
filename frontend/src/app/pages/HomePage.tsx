import { Button } from "@/shared/ui/button";

export function HomePage() {
  return (
    <section className="space-y-4">
      <h1 className="text-3xl font-bold">Mini ATS</h1>
      <p className="text-muted-foreground">Gestión de vacantes y candidatos</p>
      <Button type="button">Comenzar</Button>
    </section>
  );
}

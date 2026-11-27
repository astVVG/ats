import { StageBadge } from "@/shared/components/StageBadge";
import { ThemeToggle } from "@/shared/components/ThemeToggle";
import { STAGE_LABELS, STAGES, type Stage } from "@/shared/lib/stages";
import { useTheme } from "@/shared/theme/useTheme";
import { Alert, AlertDescription, AlertTitle } from "@/shared/ui/alert";
import { Badge } from "@/shared/ui/badge";
import { Button } from "@/shared/ui/button";
import {
  Card,
  CardContent,
  CardDescription,
  CardFooter,
  CardHeader,
  CardTitle,
} from "@/shared/ui/card";

// Full class names so Tailwind generates them.
const SWATCHES: Record<Stage, { solid: string; soft: string; text: string }> = {
  APPLIED: {
    solid: "bg-stage-applied",
    soft: "bg-stage-applied-soft",
    text: "text-stage-applied-foreground",
  },
  INTERVIEW: {
    solid: "bg-stage-interview",
    soft: "bg-stage-interview-soft",
    text: "text-stage-interview-foreground",
  },
  OFFER: {
    solid: "bg-stage-offer",
    soft: "bg-stage-offer-soft",
    text: "text-stage-offer-foreground",
  },
  HIRED: {
    solid: "bg-stage-hired",
    soft: "bg-stage-hired-soft",
    text: "text-stage-hired-foreground",
  },
  REJECTED: {
    solid: "bg-stage-rejected",
    soft: "bg-stage-rejected-soft",
    text: "text-stage-rejected-foreground",
  },
};

/** Development-only page to review the theme tokens in light and dark mode. */
export function ThemePreviewPage() {
  const { preference, resolvedTheme } = useTheme();

  return (
    <section className="space-y-8">
      <div className="flex flex-wrap items-center justify-between gap-4">
        <div className="space-y-1">
          <h1 className="text-3xl font-bold">Vista previa del tema</h1>
          <p className="text-muted-foreground">
            Preferencia: {preference} · Tema aplicado: {resolvedTheme}
          </p>
        </div>
        <ThemeToggle />
      </div>

      <div className="space-y-3">
        <h2 className="text-lg font-semibold">Botones</h2>
        <div className="flex flex-wrap gap-2">
          <Button type="button">Principal</Button>
          <Button type="button" variant="secondary">
            Secundario
          </Button>
          <Button type="button" variant="outline">
            Contorno
          </Button>
          <Button type="button" variant="ghost">
            Fantasma
          </Button>
          <Button type="button" variant="destructive">
            Destructivo
          </Button>
          <Button type="button" variant="link">
            Enlace
          </Button>
          <Button type="button" disabled>
            Deshabilitado
          </Button>
        </div>
      </div>

      <div className="space-y-3">
        <h2 className="text-lg font-semibold">Insignias</h2>
        <div className="flex flex-wrap gap-2">
          <Badge>Principal</Badge>
          <Badge variant="secondary">Secundaria</Badge>
          <Badge variant="outline">Contorno</Badge>
          <Badge variant="destructive">Destructiva</Badge>
        </div>
      </div>

      <div className="grid gap-4 md:grid-cols-2">
        <div className="space-y-3">
          <Alert>
            <AlertTitle>Aviso</AlertTitle>
            <AlertDescription>Un mensaje informativo.</AlertDescription>
          </Alert>
          <Alert variant="destructive">
            <AlertTitle>Error</AlertTitle>
            <AlertDescription>Algo salió mal.</AlertDescription>
          </Alert>
        </div>
        <Card>
          <CardHeader>
            <CardTitle>Tarjeta</CardTitle>
            <CardDescription>Texto secundario de la tarjeta.</CardDescription>
          </CardHeader>
          <CardContent>
            <p className="text-sm">Contenido sobre el fondo de tarjeta.</p>
          </CardContent>
          <CardFooter className="gap-2">
            <Button type="button" size="sm">
              Guardar
            </Button>
            <Button type="button" size="sm" variant="outline">
              Cancelar
            </Button>
          </CardFooter>
        </Card>
      </div>

      <div className="space-y-3">
        <h2 className="text-lg font-semibold">Etapas</h2>
        <div className="flex flex-wrap gap-2">
          {STAGES.map((stage) => (
            <StageBadge key={stage} stage={stage} />
          ))}
        </div>
        <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-5">
          {STAGES.map((stage) => {
            const swatch = SWATCHES[stage];
            return (
              <div key={stage} className="space-y-2 rounded-lg border p-3">
                <p className="text-sm font-medium">{STAGE_LABELS[stage]}</p>
                <div className="flex gap-2">
                  <div
                    className={`h-8 flex-1 rounded-md ${swatch.solid}`}
                    title="Sólido"
                  />
                  <div
                    className={`h-8 flex-1 rounded-md border ${swatch.soft}`}
                    title="Suave"
                  />
                </div>
                <p
                  className={`rounded-md px-2 py-1 text-sm ${swatch.soft} ${swatch.text}`}
                >
                  Texto sobre suave
                </p>
              </div>
            );
          })}
        </div>
      </div>
    </section>
  );
}

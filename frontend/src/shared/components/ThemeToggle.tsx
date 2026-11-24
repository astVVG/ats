import { Monitor, Moon, Sun, type LucideIcon } from "lucide-react";
import { cn } from "@/shared/lib/utils";
import type { ThemePreference } from "@/shared/theme/theme";
import { useTheme } from "@/shared/theme/useTheme";
import { Button } from "@/shared/ui/button";

const OPTIONS: { value: ThemePreference; label: string; icon: LucideIcon }[] = [
  { value: "light", label: "Tema claro", icon: Sun },
  { value: "dark", label: "Tema oscuro", icon: Moon },
  { value: "system", label: "Tema del sistema", icon: Monitor },
];

export function ThemeToggle({ className }: { className?: string }) {
  const { preference, setPreference } = useTheme();

  return (
    <div
      role="group"
      aria-label="Tema"
      className={cn(
        "inline-flex items-center gap-0.5 rounded-lg border bg-background p-0.5",
        className,
      )}
    >
      {OPTIONS.map(({ value, label, icon: Icon }) => {
        const active = preference === value;
        return (
          <Button
            key={value}
            type="button"
            size="icon-xs"
            variant={active ? "secondary" : "ghost"}
            aria-label={label}
            aria-pressed={active}
            title={label}
            className={cn(!active && "text-muted-foreground")}
            onClick={() => setPreference(value)}
          >
            <Icon aria-hidden="true" />
          </Button>
        );
      })}
    </div>
  );
}

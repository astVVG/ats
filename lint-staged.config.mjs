import path from "node:path";
import { fileURLToPath } from "node:url";

const root = path.dirname(fileURLToPath(import.meta.url));

const quote = (file) => `"${file.replace(/"/g, '\\"')}"`;

const relative = (file) => path.relative(root, file).split(path.sep).join("/");

const isFormattableFrontendFile = (file) => {
  const parts = relative(file).split("/");
  return (
    relative(file) !== "frontend/src/shared/api/schema.d.ts" &&
    parts.at(-1) !== "package-lock.json" &&
    !parts.includes("dist") &&
    !parts.includes("node_modules")
  );
};

const frontend = (bin, args, files) =>
  `npm --prefix frontend exec -- ${bin} ${args} ${files.map(quote).join(" ")}`;

// Spotless matches -DspotlessFiles regexes against absolute paths. Backslashes do not
// survive argument parsing, so separators become "." and special characters "[c]".
const escapeRegex = (text) => text.replace(/[.*+?${}()|]/g, "[$&]");

const spotlessPattern = (file) => ".*." + relative(file).split("/").map(escapeRegex).join(".");

export default {
  "frontend/**/*.{ts,tsx,js,jsx}": (files) => {
    const targets = files.filter(isFormattableFrontendFile);
    if (targets.length === 0) return [];
    return [
      frontend("eslint", "--fix --no-warn-ignored", targets),
      frontend("prettier", "--write", targets),
    ];
  },
  "frontend/**/*.{json,css,md,html}": (files) => {
    const targets = files.filter(isFormattableFrontendFile);
    if (targets.length === 0) return [];
    return [frontend("prettier", "--write", targets)];
  },
  "backend/**/*.java": (files) => [
    `sh backend/mvnw -q -f backend spotless:apply "-DspotlessFiles=${files.map(spotlessPattern).join(",")}"`,
  ],
};

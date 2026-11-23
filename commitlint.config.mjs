export default {
  extends: ["@commitlint/config-conventional"],
  rules: {
    "type-enum": [
      2,
      "always",
      ["feat", "fix", "refactor", "test", "chore", "docs", "perf", "build", "ci"],
    ],
    // Scope is optional; when present it must be one of these.
    "scope-enum": [2, "always", ["api", "web", "db", "infra"]],
  },
};

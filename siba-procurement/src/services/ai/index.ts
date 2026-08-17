/**
 * AI Procurement Analyst — SERVICE ABSTRACTION ONLY (no implementation yet).
 *
 * Phase 1 works entirely without an LLM: every calculation is deterministic
 * and lives in src/domain. This module defines the seam where a future AI
 * analyst plugs in, so that:
 *   - no provider SDK (OpenAI/Anthropic/…) is ever imported outside this dir,
 *   - API keys stay server-side (read from env inside a provider impl),
 *   - the provider is swappable via AI_PROVIDER env without touching callers.
 *
 * Future callers pass structured, pre-computed context (insights from
 * src/server/material-insights.ts) — the AI explains and prioritizes; it
 * never replaces the deterministic engines.
 */

export interface AnalystContext {
  /** Structured facts prepared by the server (never raw DB access). */
  facts: Record<string, unknown>;
  question: string;
}

export interface AnalystAnswer {
  answer: string;
  /** Facts the answer was based on, for traceability. */
  usedFacts: string[];
}

export interface AiAnalystProvider {
  readonly name: string;
  readonly isConfigured: boolean;
  ask(context: AnalystContext): Promise<AnalystAnswer>;
}

class NotConfiguredProvider implements AiAnalystProvider {
  readonly name = "none";
  readonly isConfigured = false;
  async ask(): Promise<AnalystAnswer> {
    throw new Error(
      "No AI provider configured. Set AI_PROVIDER and the matching API key (see .env.example) once an implementation is added."
    );
  }
}

/**
 * Provider registry. Future: "anthropic" | "openai" | … each in its own file,
 * selected here and nowhere else.
 */
export function getAiProvider(): AiAnalystProvider {
  return new NotConfiguredProvider();
}

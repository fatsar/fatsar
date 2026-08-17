import { Badge, type BadgeTone } from "@/components/ui/badge";
import {
  RECOMMENDATION_LABELS,
  type ActionPriority,
  type ActionStatus,
  type Recommendation,
  type RiskLevel,
} from "@/domain/enums";
import { titleCase } from "@/lib/format";

/**
 * Domain badges. Color is always a SECONDARY signal — the text level/state is
 * always displayed (spec requirement: never color-only risk indication).
 */

const riskTones: Record<RiskLevel, BadgeTone> = {
  LOW: "ok",
  MEDIUM: "warn",
  HIGH: "serious",
  CRITICAL: "crit",
};

export function RiskBadge({ level, score }: { level: RiskLevel; score?: number }) {
  return (
    <Badge
      tone={riskTones[level]}
      title={score != null ? `Risk score ${score}` : undefined}
    >
      {level}
    </Badge>
  );
}

const recommendationTones: Record<Recommendation, BadgeTone> = {
  URGENT_PURCHASE: "crit",
  BUY_NOW: "accent",
  SECURE_SUPPLY: "serious",
  REQUEST_QUOTATION: "accent",
  NEGOTIATE: "warn",
  WAIT: "neutral",
  MONITOR: "outline",
};

export function RecommendationBadge({ value }: { value: Recommendation }) {
  return <Badge tone={recommendationTones[value]}>{RECOMMENDATION_LABELS[value]}</Badge>;
}

const actionStatusTones: Record<ActionStatus, BadgeTone> = {
  OPEN: "accent",
  IN_PROGRESS: "warn",
  WAITING_SUPPLIER: "serious",
  WAITING_INTERNAL: "neutral",
  COMPLETED: "ok",
  CANCELLED: "outline",
};

export function ActionStatusBadge({ status }: { status: ActionStatus }) {
  return <Badge tone={actionStatusTones[status]}>{titleCase(status)}</Badge>;
}

const priorityTones: Record<ActionPriority, BadgeTone> = {
  LOW: "outline",
  MEDIUM: "neutral",
  HIGH: "serious",
  URGENT: "crit",
};

export function PriorityBadge({ priority }: { priority: ActionPriority }) {
  return <Badge tone={priorityTones[priority]}>{titleCase(priority)}</Badge>;
}

export function SampleBadge() {
  return (
    <Badge tone="outline" title="Development sample data — not real purchasing data">
      SAMPLE
    </Badge>
  );
}

export function InactiveBadge() {
  return <Badge tone="outline">INACTIVE</Badge>;
}

export function ExpiredBadge() {
  return <Badge tone="serious">EXPIRED</Badge>;
}

export type Metric = 'timeMinutes' | 'swipes';
export type RoastStage = 'gentle' | 'intermediate' | 'savage';
export type AllowanceMode = 'shared' | 'perApp';

export interface MetricLimit {
  allowance: number;
  reminderCount: number;
}

export interface LimitSettings {
  trackedApps: string[];
  allocation: Record<Metric, AllowanceMode>;
  limits: Record<Metric, MetricLimit>;
  enabled: boolean;
}

export interface DatedSettings {
  active: LimitSettings;
  pending?: {
    settings: LimitSettings;
    activatesOn: string;
  };
}

export interface MetricSnapshot {
  metric: Metric;
  scope: string;
  value: number;
  deliveredMilestones: number[];
}

export interface MetricEvaluation {
  exceeded: boolean;
  remaining: number;
  crossedMilestones: number[];
  reminder?: { milestone: number; stage: RoastStage };
}

const METRICS: Metric[] = ['timeMinutes', 'swipes'];

/** YYYY-MM-DD based on the device's local calendar, suitable for daily row keys. */
export function localDateKey(date: Date): string {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}

export function tomorrowKey(now: Date): string {
  const tomorrow = new Date(now);
  tomorrow.setHours(12, 0, 0, 0);
  tomorrow.setDate(tomorrow.getDate() + 1);
  return localDateKey(tomorrow);
}

export function stageForMilestone(index: number, count: number): RoastStage {
  if (!Number.isInteger(index) || !Number.isInteger(count) || index < 1 || index > count) {
    throw new RangeError('Milestone index must be between 1 and reminder count');
  }
  if (index === count) return 'savage';
  const fraction = index / count;
  if (fraction <= 1 / 3) return 'gentle';
  if (fraction <= 2 / 3) return 'intermediate';
  return 'savage';
}

/** Returns cumulative trigger values. Swipe values are always whole numbers. */
export function reminderMilestones(metric: Metric, limit: MetricLimit): number[] {
  const errors = validateMetricLimit(metric, limit);
  if (errors.length) throw new RangeError(errors.join('; '));
  return Array.from({ length: limit.reminderCount }, (_, i) => {
    const raw = ((i + 1) * limit.allowance) / limit.reminderCount;
    return metric === 'swipes' ? Math.ceil(raw) : raw;
  });
}

export function validateMetricLimit(metric: Metric, limit: MetricLimit): string[] {
  const errors: string[] = [];
  if (!Number.isInteger(limit.allowance) || limit.allowance <= 0) {
    errors.push('Allowance must be a positive whole number');
  }
  if (!Number.isInteger(limit.reminderCount) || limit.reminderCount <= 0) {
    errors.push('Reminder count must be a positive whole number');
  }
  if (errors.length) return errors;
  if (metric === 'swipes' && limit.reminderCount > limit.allowance) {
    errors.push('Reminder count cannot exceed the swipe allowance');
  }
  return errors;
}

export function validateSettings(settings: LimitSettings): Partial<Record<Metric | 'trackedApps', string[]>> {
  const errors: Partial<Record<Metric | 'trackedApps', string[]>> = {};
  if (new Set(settings.trackedApps).size !== settings.trackedApps.length) {
    errors.trackedApps = ['Tracked apps must be unique'];
  }
  for (const metric of METRICS) {
    const metricErrors = validateMetricLimit(metric, settings.limits[metric]);
    if (metricErrors.length) errors[metric] = metricErrors;
  }
  return errors;
}

export function evaluateMetric(snapshot: MetricSnapshot, limit: MetricLimit): MetricEvaluation {
  const milestones = reminderMilestones(snapshot.metric, limit);
  const delivered = new Set(snapshot.deliveredMilestones);
  const crossedMilestones = milestones.filter((value) => value <= snapshot.value && !delivered.has(value));
  const exceeded = snapshot.value >= limit.allowance;
  const highest = crossedMilestones.at(-1);

  return {
    exceeded,
    remaining: Math.max(0, limit.allowance - snapshot.value),
    crossedMilestones,
    // Continuous enforcement wins at the final limit, so no one-shot reminder is requested.
    reminder:
      exceeded || highest === undefined
        ? undefined
        : {
            milestone: highest,
            stage: stageForMilestone(milestones.indexOf(highest) + 1, milestones.length),
          },
  };
}

/** Initial setup is immediate; later edits are staged for the next local day. */
export function saveSettings(
  current: DatedSettings | undefined,
  next: LimitSettings,
  now: Date,
): DatedSettings {
  const errors = validateSettings(next);
  if (Object.keys(errors).length) throw new RangeError(JSON.stringify(errors));
  if (!current) return { active: next };
  return {
    active: current.active,
    pending: { settings: next, activatesOn: tomorrowKey(now) },
  };
}

/** Activates pending settings on or after their local date, including after downtime. */
export function activatePendingSettings(state: DatedSettings, now: Date): DatedSettings {
  if (!state.pending || localDateKey(now) < state.pending.activatesOn) return state;
  return { active: state.pending.settings };
}

export interface DailyAggregate {
  date: string;
  perApp: Record<string, { timeMinutes: number; swipes: number }>;
  delivered: Record<string, number[]>;
}

export function emptyDailyAggregate(now: Date): DailyAggregate {
  return { date: localDateKey(now), perApp: {}, delivered: {} };
}

/** Moves to a fresh day without mutating the row that should be persisted as history. */
export function rollDailyAggregate(
  current: DailyAggregate | undefined,
  now: Date,
): { current: DailyAggregate; completed?: DailyAggregate } {
  const today = localDateKey(now);
  if (!current) return { current: emptyDailyAggregate(now) };
  if (current.date === today) return { current };
  return { current: emptyDailyAggregate(now), completed: current };
}

export function allowanceScope(mode: AllowanceMode, appPackage: string): string {
  return mode === 'shared' ? 'shared' : appPackage;
}

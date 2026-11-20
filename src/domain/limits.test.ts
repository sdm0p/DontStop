import { describe, expect, it } from 'vitest';

import {
  activatePendingSettings,
  allowanceScope,
  emptyDailyAggregate,
  evaluateMetric,
  localDateKey,
  reminderMilestones,
  rollDailyAggregate,
  saveSettings,
  stageForMilestone,
  type LimitSettings,
} from './limits';

const settings: LimitSettings = {
  trackedApps: ['com.instagram.android'],
  allocation: { timeMinutes: 'shared', swipes: 'perApp' },
  limits: {
    timeMinutes: { allowance: 180, reminderCount: 3 },
    swipes: { allowance: 10, reminderCount: 3 },
  },
  enabled: true,
};

describe('milestones', () => {
  it('divides time limits and assigns escalating stages', () => {
    expect(reminderMilestones('timeMinutes', settings.limits.timeMinutes)).toEqual([60, 120, 180]);
    expect([1, 2, 3].map((i) => stageForMilestone(i, 3))).toEqual([
      'gentle',
      'intermediate',
      'savage',
    ]);
  });

  it('rounds fractional swipe milestones upward and keeps them distinct', () => {
    expect(reminderMilestones('swipes', settings.limits.swipes)).toEqual([4, 7, 10]);
    expect(() => reminderMilestones('swipes', { allowance: 2, reminderCount: 3 })).toThrow(
      'cannot exceed',
    );
    expect(reminderMilestones('timeMinutes', { allowance: 1, reminderCount: 3 })).toEqual([
      1 / 3,
      2 / 3,
      1,
    ]);
  });
});

describe('metric evaluation', () => {
  it('plays only the highest newly crossed reminder and consumes all crossed milestones', () => {
    expect(
      evaluateMetric(
        { metric: 'swipes', scope: 'shared', value: 8, deliveredMilestones: [] },
        settings.limits.swipes,
      ),
    ).toEqual({
      exceeded: false,
      remaining: 2,
      crossedMilestones: [4, 7],
      reminder: { milestone: 7, stage: 'intermediate' },
    });
  });

  it('prioritizes continuous enforcement and clamps remaining to zero', () => {
    expect(
      evaluateMetric(
        { metric: 'timeMinutes', scope: 'shared', value: 181, deliveredMilestones: [60] },
        settings.limits.timeMinutes,
      ),
    ).toEqual({
      exceeded: true,
      remaining: 0,
      crossedMilestones: [120, 180],
      reminder: undefined,
    });
  });
});

describe('settings activation', () => {
  it('applies initial setup immediately and later edits tomorrow', () => {
    const beforeMidnight = new Date(2026, 9, 10, 23, 59);
    const initial = saveSettings(undefined, settings, beforeMidnight);
    expect(initial).toEqual({ active: settings });

    const edited = { ...settings, enabled: false };
    const staged = saveSettings(initial, edited, beforeMidnight);
    expect(staged.active.enabled).toBe(true);
    expect(staged.pending?.activatesOn).toBe('2026-10-11');
    expect(activatePendingSettings(staged, new Date(2026, 9, 10, 23, 59))).toBe(staged);
    expect(activatePendingSettings(staged, new Date(2026, 9, 11, 0, 0))).toEqual({ active: edited });
  });
});

describe('daily rollover', () => {
  it('retains a completed row and starts clean counters at local midnight', () => {
    const old = emptyDailyAggregate(new Date(2026, 9, 10, 9));
    old.perApp['com.instagram.android'] = { timeMinutes: 25, swipes: 40 };
    old.delivered['swipes:shared'] = [34];

    const result = rollDailyAggregate(old, new Date(2026, 9, 11, 0, 1));
    expect(result.completed).toBe(old);
    expect(result.current).toEqual({ date: '2026-10-11', perApp: {}, delivered: {} });
  });

  it('uses local calendar dates and resolves allowance scopes', () => {
    expect(localDateKey(new Date(2026, 0, 2, 1))).toBe('2026-01-02');
    expect(allowanceScope('shared', 'app')).toBe('shared');
    expect(allowanceScope('perApp', 'app')).toBe('app');
  });
});

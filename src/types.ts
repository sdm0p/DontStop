export type AppId = 'instagram' | 'youtube' | 'facebook';
export type Scope = 'shared' | 'perApp';
export type RoastStage = 'calm' | 'gentle' | 'intermediate' | 'savage';

export type TrackedApp = {
  id: AppId;
  name: string;
  installed: boolean;
  enabled: boolean;
  minutesToday: number;
  swipesToday: number;
  detectionHealthy: boolean;
};

export type Allowance = {
  scope: Scope;
  sharedLimit: number;
  perAppLimits: Record<AppId, number>;
  reminders: number;
};

export type Configuration = {
  apps: Record<AppId, boolean>;
  time: Allowance;
  swipes: Allowance;
  respectDnd: boolean;
};

export type PermissionStatus = {
  usage: boolean;
  accessibility: boolean;
  overlay: boolean;
  notifications: boolean;
};

export type DayHistory = {
  date: string;
  minutes: Record<AppId, number>;
  swipes: Record<AppId, number>;
};

export type Snapshot = {
  trackingEnabled: boolean;
  permissions: PermissionStatus;
  apps: TrackedApp[];
  activeConfig: Configuration;
  pendingConfig?: Configuration;
  history: DayHistory[];
  nextMilestone?: {label: string; stage: RoastStage};
  assistantBall: string;
  assistantBallOptions: string[];
};

import {NativeEventEmitter, NativeModules, Platform} from 'react-native';
import type {AppId, Configuration, Snapshot} from '../types';

type NativeApi = {
  getSnapshot(): Promise<Snapshot>;
  saveConfiguration(config: Configuration): Promise<Snapshot>;
  setTrackingEnabled(enabled: boolean): Promise<Snapshot>;
  openPermissionSettings(permission: string): Promise<void>;
  deleteHistory(): Promise<Snapshot>;
  resetToday(metric: 'time' | 'swipes'): Promise<Snapshot>;
  setAssistantBall(value: string): Promise<Snapshot>;
  addListener?(eventName: string): void;
  removeListeners?(count: number): void;
};

type DontStopNativeModule = {
  getStatus(): Promise<{trackingEnabled?: boolean; accessibilityGranted?: boolean; usageAccessGranted?: boolean; overlayGranted?: boolean; notificationsGranted?: boolean; detectionStatus?: string}>;
  getUsageSnapshot(): Promise<{today?: NativeUsage[]; history?: {day: string; apps: NativeUsage[]}[]}>;
  getSettings(): Promise<NativeStoredSettings>;
  getAssistantBalls(): Promise<string[]>;
  setAssistantBall(value: string): Promise<void>;
  updateSettings(config: Configuration): Promise<unknown>;
  setTrackingEnabled(enabled: boolean): Promise<unknown>;
  openPermissionSettings(permission: string): Promise<void>;
  deleteHistory(): Promise<unknown>;
  resetToday(metric: 'time' | 'swipes'): Promise<void>;
  addListener?(eventName: string): void;
  removeListeners?(count: number): void;
};

type NativeUsage = {packageName: string; minutes: number; swipes: number};
type NativeStoredSettings = {enabled?: boolean; activeConfig?: Configuration; pendingConfig?: Configuration; activatesOn?: string; assistantBall?: string};

const appIds: AppId[] = ['instagram', 'youtube', 'facebook'];
const packageByApp: Record<AppId, string> = {instagram: 'com.instagram.android', youtube: 'com.google.android.youtube', facebook: 'com.facebook.katana'};
const appByPackage = Object.fromEntries(Object.entries(packageByApp).map(([app, packageName]) => [packageName, app])) as Record<string, AppId>;
const defaultConfig: Configuration = {
  apps: {instagram: true, youtube: true, facebook: false},
  time: {
    scope: 'shared',
    sharedLimit: 120,
    perAppLimits: {instagram: 60, youtube: 60, facebook: 45},
    reminders: 3,
  },
  swipes: {
    scope: 'shared',
    sharedLimit: 300,
    perAppLimits: {instagram: 150, youtube: 150, facebook: 100},
    reminders: 3,
  },
  respectDnd: true,
};

const makeHistory = () =>
  Array.from({length: 14}, (_, index) => {
    const date = new Date();
    date.setDate(date.getDate() - (13 - index));
    return {
      date: date.toISOString().slice(0, 10),
      minutes: {instagram: 18 + (index * 13) % 47, youtube: 9 + (index * 7) % 38, facebook: (index * 5) % 22},
      swipes: {instagram: 36 + (index * 31) % 150, youtube: 12 + (index * 19) % 100, facebook: (index * 11) % 60},
    };
  });

let demoSnapshot: Snapshot = {
  trackingEnabled: true,
  permissions: {usage: true, accessibility: true, overlay: true, notifications: true},
  apps: [
    {id: 'instagram', name: 'Instagram', installed: true, enabled: true, minutesToday: 42, swipesToday: 126, detectionHealthy: true},
    {id: 'youtube', name: 'YouTube', installed: true, enabled: true, minutesToday: 21, swipesToday: 48, detectionHealthy: true},
    {id: 'facebook', name: 'Facebook', installed: true, enabled: false, minutesToday: 0, swipesToday: 0, detectionHealthy: true},
  ],
  activeConfig: defaultConfig,
  history: makeHistory(),
  nextMilestone: {label: '80 min shared time', stage: 'intermediate'},
  assistantBall: 'auto',
  assistantBallOptions: ['auto'],
};

const nativeModule: DontStopNativeModule | undefined = NativeModules.DontStopNative;

const mockApi: NativeApi = {
  async getSnapshot() { return demoSnapshot; },
  async saveConfiguration(config) {
    demoSnapshot = {...demoSnapshot, activeConfig: config, apps: demoSnapshot.apps.map(app => ({...app, enabled: config.apps[app.id]}))};
    return demoSnapshot;
  },
  async setTrackingEnabled(enabled) {
    demoSnapshot = {...demoSnapshot, trackingEnabled: enabled};
    return demoSnapshot;
  },
  async openPermissionSettings() {},
  async deleteHistory() {
    demoSnapshot = {...demoSnapshot, history: []};
    return demoSnapshot;
  },
  async resetToday(metric) {
    demoSnapshot = {...demoSnapshot, apps: demoSnapshot.apps.map(app => ({...app, ...(metric === 'time' ? {minutesToday: 0} : {swipesToday: 0})}))};
    return demoSnapshot;
  },
  async setAssistantBall(value) { demoSnapshot = {...demoSnapshot, assistantBall: value}; return demoSnapshot; },
};

async function nativeSnapshot(): Promise<Snapshot> {
  if (!nativeModule) return demoSnapshot;
  const [status, usage, settings, assistantBallOptions] = await Promise.all([nativeModule.getStatus(), nativeModule.getUsageSnapshot(), nativeModule.getSettings(), nativeModule.getAssistantBalls()]);
  const activeConfig = settings.activeConfig ?? defaultConfig;
  const today = new Map((usage.today ?? []).map(row => [appByPackage[row.packageName], row]));
  const detectionHealthy = status.detectionStatus === 'available';
  const apps = demoSnapshot.apps.map(app => ({...app, enabled: activeConfig.apps[app.id], minutesToday: today.get(app.id)?.minutes ?? 0, swipesToday: today.get(app.id)?.swipes ?? 0, detectionHealthy}));
  const history = (usage.history ?? []).map(day => ({date: day.day, minutes: Object.fromEntries(appIds.map(id => [id, day.apps.find(row => appByPackage[row.packageName] === id)?.minutes ?? 0])) as Record<AppId, number>, swipes: Object.fromEntries(appIds.map(id => [id, day.apps.find(row => appByPackage[row.packageName] === id)?.swipes ?? 0])) as Record<AppId, number>}));
  return {
    ...demoSnapshot,
    trackingEnabled: status.trackingEnabled ?? settings.enabled ?? false,
    activeConfig,
    pendingConfig: settings.pendingConfig,
    permissions: {usage: status.usageAccessGranted ?? false, accessibility: status.accessibilityGranted ?? false, overlay: status.overlayGranted ?? false, notifications: status.notificationsGranted ?? false},
    apps,
    history,
    assistantBall: settings.assistantBall ?? 'auto',
    assistantBallOptions,
  };
}

const nativeApi: NativeApi | undefined = nativeModule ? {
  getSnapshot: nativeSnapshot,
  async saveConfiguration(config) { await nativeModule.updateSettings(config); return nativeSnapshot(); },
  async setTrackingEnabled(enabled) { await nativeModule.setTrackingEnabled(enabled); return nativeSnapshot(); },
  openPermissionSettings: permission => nativeModule.openPermissionSettings(permission),
  async deleteHistory() { await nativeModule.deleteHistory(); return nativeSnapshot(); },
  async resetToday(metric) { await nativeModule.resetToday(metric); return nativeSnapshot(); },
  async setAssistantBall(value) { await nativeModule.setAssistantBall(value); return nativeSnapshot(); },
} : undefined;

export const DontStopBridge = nativeApi ?? mockApi;
export const isUsingMockBridge = !nativeModule;

export function subscribeToSnapshot(onSnapshot: (snapshot: Snapshot) => void) {
  if (!nativeModule || Platform.OS !== 'android') return () => {};
  const emitter = new NativeEventEmitter(NativeModules.DontStopNative);
  const subscription = emitter.addListener('DontStopSnapshotChanged', onSnapshot);
  return () => subscription.remove();
}

export {appIds, defaultConfig};

import React from 'react';
import {Pressable, StyleSheet, Text, View} from 'react-native';
import type {AppId, Snapshot} from '../types';
import {colors} from '../theme';
import {Button, Card, Pill, Progress, SectionTitle} from '../components/Ui';

const appEmoji: Record<AppId, string> = {instagram: '◎', youtube: '▶', facebook: 'f'};

function limitFor(snapshot: Snapshot, metric: 'time' | 'swipes', appId?: AppId) {
  const allowance = snapshot.activeConfig[metric];
  return allowance.scope === 'shared' || !appId ? allowance.sharedLimit : allowance.perAppLimits[appId];
}

export function DashboardScreen({snapshot, onOpenHistory, onOpenSettings}: {snapshot: Snapshot; onOpenHistory: () => void; onOpenSettings: () => void}) {
  const enabledApps = snapshot.apps.filter(app => app.enabled);
  const totalMinutes = enabledApps.reduce((sum, app) => sum + app.minutesToday, 0);
  const totalSwipes = enabledApps.reduce((sum, app) => sum + app.swipesToday, 0);
  const missing = Object.entries(snapshot.permissions).filter(([, ok]) => !ok).map(([name]) => name);
  const healthy = snapshot.trackingEnabled && missing.length === 0;

  return <>
    <View style={styles.header}>
      <View><Text style={styles.logo}>DONTSTOP</Text><Text style={styles.tagline}>Keep going. We’re watching.</Text></View>
      <Pressable accessibilityRole="button" accessibilityLabel="Open settings" hitSlop={12} onPress={onOpenSettings} style={styles.iconButton}><Text style={styles.gear}>⚙</Text></Pressable>
    </View>

    <Card tone={healthy ? 'mint' : 'coral'}>
      <View style={styles.rowBetween}>
        <Pill label={healthy ? 'TRACKING ACTIVE' : snapshot.trackingEnabled ? 'NEEDS ATTENTION' : 'TRACKING OFF'} tone={healthy ? 'mint' : 'coral'} />
        <Text style={styles.eye}>◉</Text>
      </View>
      <Text style={styles.cardTitle}>{healthy ? 'Your thumb has been busy.' : missing.length ? `${missing.join(', ')} access is missing.` : 'The watcher is taking a break.'}</Text>
      <Text style={styles.body}>{healthy ? 'Today’s totals are caught up.' : 'Open Settings to repair tracking.'}</Text>
    </Card>

    <SectionTitle>Today’s damage</SectionTitle>
    <View style={styles.metricRow}>
      <Card tone="yellow" style={styles.metricCard}>
        <Text style={styles.metricLabel}>TIME</Text><Text style={styles.metricValue}>{totalMinutes}<Text style={styles.metricUnit}> min</Text></Text>
        <Progress value={totalMinutes} max={limitFor(snapshot, 'time')} color={colors.purple} />
        <Text style={styles.caption}>{Math.max(0, limitFor(snapshot, 'time') - totalMinutes)} min remaining · {snapshot.activeConfig.time.scope === 'shared' ? 'shared' : 'per app'}</Text>
      </Card>
      <Card tone="purple" style={styles.metricCard}>
        <Text style={styles.metricLabel}>SWIPES</Text><Text style={styles.metricValue}>{totalSwipes}</Text>
        <Progress value={totalSwipes} max={limitFor(snapshot, 'swipes')} color={colors.coral} />
        <Text style={styles.caption}>{Math.max(0, limitFor(snapshot, 'swipes') - totalSwipes)} remaining · {snapshot.activeConfig.swipes.scope === 'shared' ? 'shared' : 'per app'}</Text>
      </Card>
    </View>

    {snapshot.nextMilestone && <Card><Pill label={`${snapshot.nextMilestone.stage.toUpperCase()} NEXT`} tone={snapshot.nextMilestone.stage === 'savage' ? 'coral' : snapshot.nextMilestone.stage === 'gentle' ? 'yellow' : 'purple'} /><Text style={styles.cardTitle}>{snapshot.nextMilestone.label}</Text><Text style={styles.body}>Your next completely voluntary reminder.</Text></Card>}

    <SectionTitle>App breakdown</SectionTitle>
    {enabledApps.map(app => {
      const timeLimit = limitFor(snapshot, 'time', app.id);
      return <Card key={app.id}>
        <View style={styles.appRow}>
          <View style={styles.appIcon}><Text style={styles.appIconText}>{appEmoji[app.id]}</Text></View>
          <View style={styles.appInfo}><Text style={styles.appName}>{app.name}</Text><Text style={styles.body}>{app.minutesToday} min · {app.swipesToday} short-video swipes</Text></View>
          <Pill label={app.detectionHealthy ? 'OK' : 'DEGRADED'} tone={app.detectionHealthy ? 'mint' : 'coral'} />
        </View>
        {snapshot.activeConfig.time.scope === 'perApp' && <Progress value={app.minutesToday} max={timeLimit} />}
      </Card>;
    })}

    <Button label="See 30-day history" variant="secondary" onPress={onOpenHistory} />
  </>;
}

const styles = StyleSheet.create({
  header: {flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginBottom: 18},
  logo: {fontSize: 31, fontWeight: '900', color: colors.ink, letterSpacing: -1},
  tagline: {fontSize: 13, color: colors.muted, fontWeight: '700'},
  iconButton: {width: 48, height: 48, borderWidth: 2, borderColor: colors.ink, borderRadius: 16, backgroundColor: colors.yellow, alignItems: 'center', justifyContent: 'center'},
  gear: {fontSize: 23, color: colors.ink}, eye: {fontSize: 34, color: colors.ink},
  rowBetween: {flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between'},
  cardTitle: {fontSize: 20, fontWeight: '900', color: colors.ink, marginTop: 12},
  body: {fontSize: 14, lineHeight: 20, color: colors.muted, marginTop: 3},
  metricRow: {flexDirection: 'row', gap: 12}, metricCard: {flex: 1},
  metricLabel: {fontSize: 12, letterSpacing: 1.2, fontWeight: '900', color: colors.ink},
  metricValue: {fontSize: 30, fontWeight: '900', color: colors.ink, marginTop: 7}, metricUnit: {fontSize: 15},
  caption: {fontSize: 11, lineHeight: 16, color: colors.ink, marginTop: 8, fontWeight: '700'},
  appRow: {flexDirection: 'row', alignItems: 'center'},
  appIcon: {width: 46, height: 46, borderWidth: 2, borderColor: colors.ink, borderRadius: 14, backgroundColor: colors.yellow, alignItems: 'center', justifyContent: 'center', marginRight: 12},
  appIconText: {fontSize: 22, fontWeight: '900', color: colors.ink}, appInfo: {flex: 1}, appName: {fontSize: 17, fontWeight: '900', color: colors.ink},
});

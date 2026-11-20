import React, {useEffect, useState} from 'react';
import {ActivityIndicator, Alert, AppState, ScrollView, StatusBar, StyleSheet, Text, View} from 'react-native';
import {SafeAreaProvider, SafeAreaView} from 'react-native-safe-area-context';
import {DashboardScreen} from './src/screens/DashboardScreen';
import {HistoryScreen} from './src/screens/HistoryScreen';
import {OnboardingScreen} from './src/screens/OnboardingScreen';
import {SettingsScreen} from './src/screens/SettingsScreen';
import {DontStopBridge, isUsingMockBridge, subscribeToSnapshot} from './src/native/DontStopBridge';
import {colors} from './src/theme';
import type {Configuration, Snapshot} from './src/types';

type Screen = 'dashboard' | 'history' | 'settings';

function DontStopApp() {
  const [snapshot, setSnapshot] = useState<Snapshot>();
  const [screen, setScreen] = useState<Screen>('dashboard');
  const [onboarded, setOnboarded] = useState(false);
  const [error, setError] = useState<string>();

  useEffect(() => {
    const load = () => DontStopBridge.getSnapshot().then(next => {
      setSnapshot(next);
      setOnboarded(!isUsingMockBridge && next.trackingEnabled);
    }).catch(reason => setError(String(reason)));
    load();
    const appState = AppState.addEventListener('change', state => { if (state === 'active') load(); });
    const unsubscribe = subscribeToSnapshot(setSnapshot);
    return () => { appState.remove(); unsubscribe(); };
  }, []);

  const refresh = async (promise: Promise<Snapshot>) => {
    try {
      const next = await promise;
      setSnapshot(next);
      setError(undefined);
      return next;
    } catch (reason) {
      setError(String(reason));
      throw reason;
    }
  };
  const saveConfig = async (config: Configuration, initial = false) => {
    await refresh(DontStopBridge.saveConfiguration(config));
    if (initial) await refresh(DontStopBridge.setTrackingEnabled(true));
    if (initial) setOnboarded(true);
    setScreen('dashboard');
    if (!initial) {
      Alert.alert(
        'Changes applied',
        'The new limits are active now. Today’s existing time and swipe totals were kept. Use the reset buttons before a fresh test.',
      );
    }
  };

  if (!snapshot) return <SafeAreaView style={styles.loading}><ActivityIndicator size="large" color={colors.purple} /><Text style={styles.loadingText}>{error ?? 'Waking the watcher…'}</Text></SafeAreaView>;

  return <SafeAreaView style={styles.safe} edges={['top', 'bottom']}>
    <StatusBar barStyle="dark-content" backgroundColor={colors.canvas} />
    {isUsingMockBridge && <View style={styles.demo}><Text style={styles.demoText}>DEMO DATA · native tracker not connected</Text></View>}
    <ScrollView contentContainerStyle={styles.content} keyboardShouldPersistTaps="handled">
      {!onboarded ? <OnboardingScreen permissions={snapshot.permissions} onOpenPermission={permission => DontStopBridge.openPermissionSettings(permission)} onComplete={config => saveConfig(config, true)} /> : screen === 'dashboard' ? <DashboardScreen snapshot={snapshot} onOpenHistory={() => setScreen('history')} onOpenSettings={() => setScreen('settings')} /> : screen === 'history' ? <HistoryScreen snapshot={snapshot} onBack={() => setScreen('dashboard')} /> : <SettingsScreen snapshot={snapshot} onBack={() => setScreen('dashboard')} onSave={config => saveConfig(config)} onToggleTracking={enabled => refresh(DontStopBridge.setTrackingEnabled(enabled))} onAssistantBall={value => refresh(DontStopBridge.setAssistantBall(value))} onPermission={permission => DontStopBridge.openPermissionSettings(permission)} onResetToday={metric => refresh(DontStopBridge.resetToday(metric)).then(() => Alert.alert('Counter reset', metric === 'time' ? 'Today’s tracked time is now zero.' : 'Today’s swipe count is now zero.'))} onDeleteHistory={() => refresh(DontStopBridge.deleteHistory()).then(() => Alert.alert('History deleted', 'Today’s active enforcement counters were kept.'))} />}
      {error && <Text style={styles.error}>{error}</Text>}
    </ScrollView>
  </SafeAreaView>;
}

export default function App() {
  return <SafeAreaProvider><DontStopApp /></SafeAreaProvider>;
}

const styles = StyleSheet.create({
  safe: {flex: 1, backgroundColor: colors.canvas}, content: {paddingHorizontal: 18, paddingTop: 16, paddingBottom: 38, flexGrow: 1}, loading: {flex: 1, backgroundColor: colors.canvas, alignItems: 'center', justifyContent: 'center'}, loadingText: {marginTop: 14, color: colors.muted, fontWeight: '700'}, demo: {backgroundColor: colors.ink, paddingVertical: 5, alignItems: 'center'}, demoText: {color: colors.yellow, fontSize: 10, fontWeight: '900', letterSpacing: 0.7}, error: {padding: 12, backgroundColor: '#FFD6D2', color: '#8F211B', fontWeight: '800', borderRadius: 10, marginTop: 14},
});

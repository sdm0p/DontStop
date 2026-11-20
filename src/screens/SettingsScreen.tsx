import React, {useState} from 'react';
import {Alert, Pressable, StyleSheet, Switch, Text, TextInput, View} from 'react-native';
import {Button, Card, SectionTitle} from '../components/Ui';
import {colors} from '../theme';
import type {Allowance, AppId, Configuration, Snapshot} from '../types';

const appNames: Record<AppId, string> = {instagram: 'Instagram', youtube: 'YouTube', facebook: 'Facebook'};

function NumberField({label, value, onChange, suffix}: {label: string; value: number; onChange: (value: number) => void; suffix?: string}) {
  return <View style={styles.field}><Text style={styles.label}>{label}</Text><View style={styles.inputWrap}><TextInput accessibilityLabel={label} keyboardType="number-pad" value={String(value)} onChangeText={text => onChange(Number(text.replace(/\D/g, '')) || 0)} style={styles.input} /><Text style={styles.suffix}>{suffix}</Text></View></View>;
}

function AllowanceEditor({title, allowance, unit, onChange}: {title: string; allowance: Allowance; unit: string; onChange: (next: Allowance) => void}) {
  return <Card>
    <Text style={styles.cardTitle}>{title}</Text>
    <View style={styles.scopeRow}>{(['shared', 'perApp'] as const).map(scope => <Pressable key={scope} onPress={() => onChange({...allowance, scope})} style={[styles.scope, allowance.scope === scope && styles.scopeSelected]}><Text style={styles.scopeText}>{scope === 'shared' ? 'Shared' : 'Per app'}</Text></Pressable>)}</View>
    {allowance.scope === 'shared' ? <NumberField label={`${title} limit`} value={allowance.sharedLimit} suffix={unit} onChange={sharedLimit => onChange({...allowance, sharedLimit})} /> : (Object.keys(appNames) as AppId[]).map(id => <NumberField key={id} label={appNames[id]} value={allowance.perAppLimits[id]} suffix={unit} onChange={value => onChange({...allowance, perAppLimits: {...allowance.perAppLimits, [id]: value}})} />)}
    <NumberField label="Reminder count" value={allowance.reminders} onChange={reminders => onChange({...allowance, reminders})} />
    {(allowance.reminders < 1 || (title === 'Swipe allowance' && allowance.reminders > (allowance.scope === 'shared' ? allowance.sharedLimit : Math.min(...Object.values(allowance.perAppLimits))))) && <Text style={styles.error}>Choose at least one distinct reminder within the allowance.</Text>}
  </Card>;
}

export function SettingsScreen({snapshot, onSave, onToggleTracking, onAssistantBall, onDeleteHistory, onResetToday, onPermission, onBack}: {snapshot: Snapshot; onSave: (config: Configuration) => void; onToggleTracking: (enabled: boolean) => void; onAssistantBall: (value: string) => void; onDeleteHistory: () => void; onResetToday: (metric: 'time' | 'swipes') => void; onPermission: (permission: string) => void; onBack: () => void}) {
  const [draft, setDraft] = useState(snapshot.pendingConfig ?? snapshot.activeConfig);
  const enabledCount = Object.values(draft.apps).filter(Boolean).length;
  const valid = enabledCount > 0 && draft.time.reminders > 0 && draft.swipes.reminders > 0 && draft.time.sharedLimit > 0 && draft.swipes.sharedLimit > 0;
  return <>
    <View style={styles.header}><Pressable accessibilityRole="button" onPress={onBack} style={styles.back}><Text style={styles.backText}>←</Text></Pressable><Text style={styles.title}>Settings</Text></View>
    <SectionTitle>Tracking</SectionTitle>
    <Card tone={snapshot.trackingEnabled ? 'mint' : 'coral'}><View style={styles.switchRow}><View style={styles.grow}><Text style={styles.cardTitle}>{snapshot.trackingEnabled ? 'Tracking enabled' : 'Tracking disabled'}</Text><Text style={styles.body}>Re-enabling keeps today’s usage and resumes enforcement.</Text></View><Switch value={snapshot.trackingEnabled} onValueChange={onToggleTracking} trackColor={{true: colors.purple}} /></View></Card>
    <SectionTitle>Connected apps</SectionTitle>
    <Card>{(Object.keys(appNames) as AppId[]).map(id => <View key={id} style={styles.switchRow}><Text style={styles.appName}>{appNames[id]}</Text><Switch value={draft.apps[id]} onValueChange={enabled => setDraft({...draft, apps: {...draft.apps, [id]: enabled}})} trackColor={{true: colors.purple}} /></View>)}{enabledCount === 0 && <Text style={styles.error}>Connect at least one app.</Text>}</Card>
    <AllowanceEditor title="Time allowance" allowance={draft.time} unit="min" onChange={time => setDraft({...draft, time})} />
    <AllowanceEditor title="Swipe allowance" allowance={draft.swipes} unit="swipes" onChange={swipes => setDraft({...draft, swipes})} />
    <Card><View style={styles.switchRow}><View style={styles.grow}><Text style={styles.cardTitle}>Respect silent and DND</Text><Text style={styles.body}>Recommended. Device rules can prevent override behavior.</Text></View><Switch value={draft.respectDnd} onValueChange={respectDnd => setDraft({...draft, respectDnd})} trackColor={{true: colors.purple}} /></View></Card>
    <Button label="Apply changes now" disabled={!valid} onPress={() => onSave(draft)} />
    <SectionTitle>Assistant ball</SectionTitle>
    <Card><Text style={styles.cardTitle}>Choose your watcher</Text><Text style={styles.body}>Your choice updates the floating assistant ball immediately.</Text><View style={styles.ballChoices}>{snapshot.assistantBallOptions.map(option => <Pressable key={option} accessibilityRole="radio" accessibilityState={{selected: snapshot.assistantBall === option}} onPress={() => onAssistantBall(option)} style={[styles.ballChoice, snapshot.assistantBall === option && styles.ballChoiceSelected]}><View style={[styles.radio, snapshot.assistantBall === option && styles.radioSelected]} /> <Text style={styles.ballChoiceText}>{option === 'auto' ? 'Surprise me' : option.replace(/\.[^.]+$/, '').replace(/[_-]+/g, ' ')}</Text></Pressable>)}</View></Card>
    <SectionTitle>Permissions</SectionTitle>
    {(Object.entries(snapshot.permissions) as [string, boolean][]).map(([permission, ok]) => <View key={permission} style={styles.permissionRow}><View><Text style={styles.appName}>{permission[0].toUpperCase() + permission.slice(1)}</Text><Text style={styles.body}>{ok ? 'Allowed' : 'Required for full tracking'}</Text></View><Button label={ok ? 'Review' : 'Fix'} variant="secondary" onPress={() => onPermission(permission)} /></View>)}
    <SectionTitle>Data</SectionTitle>
    <Button label="Reset today's time" variant="secondary" onPress={() => Alert.alert("Reset today's time?", 'Today’s tracked minutes and time reminders will return to zero.', [{text: 'Cancel', style: 'cancel'}, {text: 'Reset', style: 'destructive', onPress: () => onResetToday('time')}])} />
    <Button label="Reset today's swipes" variant="secondary" onPress={() => Alert.alert("Reset today's swipes?", 'Today’s swipe count and swipe reminders will return to zero.', [{text: 'Cancel', style: 'cancel'}, {text: 'Reset', style: 'destructive', onPress: () => onResetToday('swipes')}])} />
    <Button label="Delete usage history" variant="danger" onPress={() => Alert.alert('Delete history?', 'Past history will be removed. Today’s active enforcement counters stay intact.', [{text: 'Cancel', style: 'cancel'}, {text: 'Delete', style: 'destructive', onPress: onDeleteHistory}])} />
  </>;
}

const styles = StyleSheet.create({
  header: {flexDirection: 'row', alignItems: 'center', gap: 12, marginBottom: 10}, back: {width: 48, height: 48, borderWidth: 2, borderColor: colors.ink, borderRadius: 15, alignItems: 'center', justifyContent: 'center', backgroundColor: colors.surface}, backText: {fontSize: 25, fontWeight: '900', color: colors.ink}, title: {fontSize: 30, fontWeight: '900', color: colors.ink},
  cardTitle: {fontSize: 18, fontWeight: '900', color: colors.ink}, body: {fontSize: 13, lineHeight: 18, color: colors.muted, marginTop: 3}, appName: {fontSize: 16, fontWeight: '800', color: colors.ink},
  switchRow: {minHeight: 58, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', gap: 12}, grow: {flex: 1},
  scopeRow: {flexDirection: 'row', gap: 8, marginVertical: 12}, scope: {flex: 1, minHeight: 44, alignItems: 'center', justifyContent: 'center', borderWidth: 1.5, borderColor: colors.ink, borderRadius: 12, backgroundColor: colors.surface}, scopeSelected: {backgroundColor: colors.yellow}, scopeText: {fontWeight: '900', color: colors.ink},
  field: {marginTop: 10}, label: {fontWeight: '800', color: colors.ink, marginBottom: 5}, inputWrap: {flexDirection: 'row', alignItems: 'center', borderWidth: 1.5, borderColor: colors.ink, borderRadius: 12, backgroundColor: '#FFFDF8'}, input: {flex: 1, minHeight: 48, paddingHorizontal: 12, fontSize: 17, fontWeight: '800', color: colors.ink}, suffix: {paddingRight: 12, color: colors.muted, fontWeight: '700'}, error: {color: '#A5221C', fontWeight: '800', marginTop: 8},
  permissionRow: {flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', borderBottomWidth: 1, borderBottomColor: '#D8D0C6', paddingVertical: 8},
  ballChoices: {marginTop: 12, gap: 8}, ballChoice: {minHeight: 46, flexDirection: 'row', alignItems: 'center', gap: 10, paddingHorizontal: 12, borderWidth: 1.5, borderColor: colors.ink, borderRadius: 13, backgroundColor: '#FFFDF8'}, ballChoiceSelected: {backgroundColor: '#EEE5FF', borderColor: colors.purple}, ballChoiceText: {fontWeight: '900', color: colors.ink, textTransform: 'capitalize'}, radio: {width: 18, height: 18, borderRadius: 9, borderWidth: 2, borderColor: colors.ink}, radioSelected: {borderWidth: 5, borderColor: colors.purple},
});

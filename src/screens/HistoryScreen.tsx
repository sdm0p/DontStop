import React, {useMemo, useState} from 'react';
import {Pressable, StyleSheet, Text, View} from 'react-native';
import {Button, Card, Pill, SectionTitle} from '../components/Ui';
import {colors} from '../theme';
import type {AppId, Snapshot} from '../types';

export function HistoryScreen({snapshot, onBack}: {snapshot: Snapshot; onBack: () => void}) {
  const [metric, setMetric] = useState<'minutes' | 'swipes'>('minutes');
  const [filter, setFilter] = useState<AppId | 'all'>('all');
  const rows = useMemo(() => snapshot.history.map(day => ({date: day.date, value: filter === 'all' ? Object.values(day[metric]).reduce((a, b) => a + b, 0) : day[metric][filter]})), [snapshot.history, metric, filter]);
  const max = Math.max(1, ...rows.map(row => row.value));
  return <>
    <View style={styles.header}><Pressable accessibilityRole="button" onPress={onBack} style={styles.back}><Text style={styles.backText}>←</Text></Pressable><Text style={styles.title}>History</Text></View>
    <Text style={styles.lead}>Receipts from your scrolling era.</Text>
    <View style={styles.toggleRow}>
      <Pressable onPress={() => setMetric('minutes')} style={[styles.toggle, metric === 'minutes' && styles.toggleActive]}><Text style={styles.toggleText}>Time</Text></Pressable>
      <Pressable onPress={() => setMetric('swipes')} style={[styles.toggle, metric === 'swipes' && styles.toggleActive]}><Text style={styles.toggleText}>Swipes</Text></Pressable>
    </View>
    <View style={styles.filters}>{(['all', 'instagram', 'youtube', 'facebook'] as const).map(item => <Pressable key={item} onPress={() => setFilter(item)}><Pill label={item === 'all' ? 'All apps' : item[0].toUpperCase() + item.slice(1)} tone={filter === item ? 'yellow' : 'purple'} /></Pressable>)}</View>
    <SectionTitle>Last {rows.length || 30} days</SectionTitle>
    {rows.length === 0 ? <Card tone="yellow"><Text style={styles.empty}>Your scrolling record starts here.</Text></Card> : <Card>
      <View accessibilityLabel={`${metric} history chart`} style={styles.chart}>{rows.map(row => <View key={row.date} style={styles.barSlot}><View style={[styles.bar, {height: Math.max(5, row.value / max * 128)}]} /><Text style={styles.axis}>{row.date.slice(8)}</Text></View>)}</View>
      <Text style={styles.chartCaption}>Highest day: {max} {metric === 'minutes' ? 'minutes' : 'swipes'}. Exact daily values appear below.</Text>
    </Card>}
    {rows.slice().reverse().map(row => <View key={row.date} style={styles.historyRow}><Text style={styles.date}>{row.date}</Text><Text style={styles.value}>{row.value} {metric === 'minutes' ? 'min' : 'swipes'}</Text></View>)}
    <Button label="Back to today" onPress={onBack} />
  </>;
}

const styles = StyleSheet.create({
  header: {flexDirection: 'row', alignItems: 'center', gap: 12}, back: {width: 48, height: 48, borderWidth: 2, borderColor: colors.ink, borderRadius: 15, alignItems: 'center', justifyContent: 'center', backgroundColor: colors.surface}, backText: {fontSize: 25, fontWeight: '900', color: colors.ink}, title: {fontSize: 30, fontWeight: '900', color: colors.ink},
  lead: {color: colors.muted, fontSize: 15, marginVertical: 12},
  toggleRow: {flexDirection: 'row', backgroundColor: '#EAE3D9', borderRadius: 15, padding: 4, marginBottom: 12}, toggle: {flex: 1, minHeight: 44, alignItems: 'center', justifyContent: 'center', borderRadius: 12}, toggleActive: {backgroundColor: colors.yellow, borderWidth: 1.5, borderColor: colors.ink}, toggleText: {fontWeight: '900', color: colors.ink},
  filters: {flexDirection: 'row', flexWrap: 'wrap', gap: 7, marginBottom: 8}, chart: {height: 155, flexDirection: 'row', alignItems: 'flex-end', gap: 4}, barSlot: {flex: 1, alignItems: 'center'}, bar: {width: '80%', backgroundColor: colors.purple, borderWidth: 1, borderColor: colors.ink, borderRadius: 4}, axis: {fontSize: 9, color: colors.muted, marginTop: 4}, chartCaption: {fontSize: 12, color: colors.muted, marginTop: 12},
  empty: {fontSize: 18, fontWeight: '900', color: colors.ink}, historyRow: {minHeight: 48, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', borderBottomWidth: 1, borderBottomColor: '#D8D0C6'}, date: {color: colors.muted}, value: {fontWeight: '900', color: colors.ink},
});

import React, {type ReactNode} from 'react';
import {Pressable, StyleSheet, Text, View, type ViewStyle} from 'react-native';
import {colors} from '../theme';

export function Card({children, tone = 'plain', style}: {children: ReactNode; tone?: 'plain' | 'purple' | 'yellow' | 'coral' | 'mint'; style?: ViewStyle}) {
  return <View style={[styles.card, toneStyles[tone], style]}>{children}</View>;
}

export function Button({label, onPress, variant = 'primary', disabled = false}: {label: string; onPress: () => void; variant?: 'primary' | 'secondary' | 'danger'; disabled?: boolean}) {
  return (
    <Pressable accessibilityRole="button" accessibilityLabel={label} disabled={disabled} onPress={onPress} style={({pressed}) => [styles.button, buttonStyles[variant], pressed && !disabled && styles.pressed, disabled && styles.disabled]}>
      <Text style={[styles.buttonText, variant === 'secondary' && styles.secondaryText]}>{label}</Text>
    </Pressable>
  );
}

export function Pill({label, tone = 'purple'}: {label: string; tone?: 'purple' | 'yellow' | 'coral' | 'mint'}) {
  return <View style={[styles.pill, toneStyles[tone]]}><Text style={styles.pillText}>{label}</Text></View>;
}

export function SectionTitle({children, action}: {children: ReactNode; action?: ReactNode}) {
  return <View style={styles.sectionTitle}><Text style={styles.sectionText}>{children}</Text>{action}</View>;
}

export function Progress({value, max, color = colors.purple}: {value: number; max: number; color?: string}) {
  const progress = max <= 0 ? 0 : Math.min(1, Math.max(0, value / max));
  return (
    <View accessible accessibilityRole="progressbar" accessibilityValue={{min: 0, max, now: Math.min(value, max)}} style={styles.progressTrack}>
      <View style={[styles.progressFill, {width: `${progress * 100}%`, backgroundColor: color}]} />
    </View>
  );
}

const toneStyles = StyleSheet.create({
  plain: {backgroundColor: colors.surface},
  purple: {backgroundColor: colors.purpleSoft},
  yellow: {backgroundColor: colors.yellow},
  coral: {backgroundColor: '#FFD6D2'},
  mint: {backgroundColor: '#DDF6E9'},
});

const buttonStyles = StyleSheet.create({
  primary: {backgroundColor: colors.purple},
  secondary: {backgroundColor: colors.surface},
  danger: {backgroundColor: colors.coral},
});

const styles = StyleSheet.create({
  card: {borderWidth: 2, borderColor: colors.border, borderRadius: 20, padding: 16, marginBottom: 14, shadowColor: colors.ink, shadowOffset: {width: 4, height: 4}, shadowOpacity: 1, shadowRadius: 0, elevation: 3},
  button: {minHeight: 50, borderRadius: 15, borderWidth: 2, borderColor: colors.ink, alignItems: 'center', justifyContent: 'center', paddingHorizontal: 18, marginVertical: 5},
  buttonText: {color: '#FFFFFF', fontWeight: '900', fontSize: 16},
  secondaryText: {color: colors.ink},
  pressed: {transform: [{translateX: 2}, {translateY: 2}]},
  disabled: {opacity: 0.45},
  pill: {borderWidth: 1.5, borderColor: colors.ink, borderRadius: 99, paddingHorizontal: 10, paddingVertical: 5, alignSelf: 'flex-start'},
  pillText: {fontWeight: '800', color: colors.ink, fontSize: 12},
  sectionTitle: {flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', marginTop: 8, marginBottom: 12},
  sectionText: {fontSize: 21, fontWeight: '900', color: colors.ink},
  progressTrack: {height: 12, borderRadius: 99, borderWidth: 1.5, borderColor: colors.ink, overflow: 'hidden', backgroundColor: '#EFE9DF', marginTop: 10},
  progressFill: {height: '100%'},
});

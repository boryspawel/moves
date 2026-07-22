export const MOVEMENT_PATTERNS = ['SQUAT', 'HINGE', 'PUSH', 'PULL', 'LUNGE', 'CARRY', 'ROTATION', 'LOCOMOTION', 'BREATHING', 'MOBILITY', 'OTHER'] as const;
export const TECHNICAL_LEVELS = ['FOUNDATIONAL', 'INTERMEDIATE', 'ADVANCED'] as const;
export const EQUIPMENT_OPTIONS = ['band', 'barbell', 'dumbbell', 'kettlebell', 'bodyweight'] as const;

const labels: Record<string, string> = {
  SQUAT: 'Przysiad', HINGE: 'Zgięcie biodrowe', PUSH: 'Pchanie', PULL: 'Przyciąganie',
  LUNGE: 'Wykrok', CARRY: 'Przenoszenie', ROTATION: 'Rotacja', LOCOMOTION: 'Lokomocja',
  BREATHING: 'Oddychanie', MOBILITY: 'Mobilność', OTHER: 'Inne',
  FOUNDATIONAL: 'Podstawowy', INTERMEDIATE: 'Średniozaawansowany', ADVANCED: 'Zaawansowany',
  HOME: 'Dom', GYM: 'Siłownia', OUTDOOR: 'Na zewnątrz', CLINIC: 'Gabinet', ANY: 'Dowolne',
  PRIMARY: 'Główna', SECONDARY: 'Wspierająca', STABILIZER: 'Stabilizująca',
  DYNAMIC: 'Dynamiczna', STATIC: 'Statyczna', MIXED: 'Mieszana', SAGITTAL: 'Strzałkowa',
  FRONTAL: 'Czołowa', TRANSVERSE: 'Poprzeczna', FULL: 'Pełny', PARTIAL: 'Częściowy'
};
export const catalogLabel = (value: string | null | undefined) => value ? (labels[value] ?? value) : '—';

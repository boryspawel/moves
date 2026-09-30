import {
  VersionCommandEnvironmentEnum,
  VersionCommandFatigueProfileEnum,
  VersionCommandMovementPatternsEnum,
  VersionCommandStimulusTypeEnum,
  VersionCommandTechnicalLevelEnum,
} from '../api/generated/src';

type Option<Value extends string = string> = { value: Value; label: string };

const movementPatternLabels = {
  SQUAT: 'Przysiad', HINGE: 'Zgięcie w biodrze (hinge)', PUSH: 'Pchanie', PULL: 'Przyciąganie',
  LUNGE: 'Wykrok', CARRY: 'Przenoszenie', ROTATION: 'Rotacja', LOCOMOTION: 'Lokomocja',
  BREATHING: 'Oddychanie', MOBILITY: 'Mobilność', OTHER: 'Inny',
} satisfies Record<VersionCommandMovementPatternsEnum, string>;
const stimulusLabels = {
  STRENGTH: 'Siła', ENDURANCE: 'Wytrzymałość', POWER: 'Moc', MOBILITY: 'Mobilność',
  BALANCE: 'Równowaga', MOTOR_CONTROL: 'Kontrola motoryczna', RECOVERY: 'Regeneracja',
} satisfies Record<VersionCommandStimulusTypeEnum, string>;
const fatigueLabels = { LOW: 'Niski', MODERATE: 'Umiarkowany', HIGH: 'Wysoki' } satisfies Record<VersionCommandFatigueProfileEnum, string>;
const technicalLevelLabels = { FOUNDATIONAL: 'Podstawowy', INTERMEDIATE: 'Średniozaawansowany', ADVANCED: 'Zaawansowany' } satisfies Record<VersionCommandTechnicalLevelEnum, string>;
const environmentLabels = { HOME: 'Dom', GYM: 'Siłownia', OUTDOOR: 'Na zewnątrz', CLINIC: 'Gabinet / placówka', ANY: 'Dowolne' } satisfies Record<VersionCommandEnvironmentEnum, string>;

const options = <Value extends string>(values: readonly Value[], labels: Record<Value, string>): Option<Value>[] =>
  values.map(value => ({value, label: labels[value]}));

export const movementPatternOptions = options(Object.values(VersionCommandMovementPatternsEnum), movementPatternLabels);
export const stimulusOptions = options(Object.values(VersionCommandStimulusTypeEnum), stimulusLabels);
export const fatigueOptions = options(Object.values(VersionCommandFatigueProfileEnum), fatigueLabels);
export const technicalLevelOptions = options(Object.values(VersionCommandTechnicalLevelEnum), technicalLevelLabels);
export const environmentOptions = options(Object.values(VersionCommandEnvironmentEnum), environmentLabels);

const equipmentOptions: Option[] = [
  ['BODYWEIGHT', 'Masa własnego ciała'], ['MAT', 'Mata'], ['WALL', 'Ściana'], ['CHAIR', 'Krzesło'], ['BENCH', 'Ławka'],
  ['BOX', 'Skrzynia / podest'], ['DUMBBELL', 'Hantel'], ['BARBELL', 'Sztanga'], ['KETTLEBELL', 'Kettlebell'], ['RESISTANCE_BAND', 'Guma oporowa'],
  ['CABLE', 'Wyciąg'], ['MACHINE', 'Maszyna'], ['MEDICINE_BALL', 'Piłka lekarska'], ['STABILITY_BALL', 'Piłka gimnastyczna'], ['PULL_UP_BAR', 'Drążek'],
  ['PARALLEL_BARS', 'Poręcze'], ['SUSPENSION_TRAINER', 'Taśmy podwieszane'], ['FOAM_ROLLER', 'Roller'], ['STEP', 'Step'], ['OTHER', 'Inny'],
].map(([value, label]) => ({value, label}));

export const equipmentOptionsFor = (selected: readonly string[]): Option[] => {
  const known = new Set(equipmentOptions.map(option => option.value));
  return [...equipmentOptions, ...selected.filter(value => !known.has(value)).map(value => ({value, label: value}))];
};

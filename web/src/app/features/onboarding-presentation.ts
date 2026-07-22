import { StateStageEnum, type State } from '../api/generated/src';

export type OnboardingPresentationStage = 'profile-type' | 'legal' | 'basic-profile' | 'availability' | 'complete';

const presentationStages: Record<NonNullable<State['stage']>, OnboardingPresentationStage> = {
  [StateStageEnum.ProfileTypeRequired]: 'profile-type',
  [StateStageEnum.LegalRequired]: 'legal',
  [StateStageEnum.ProfileRequired]: 'basic-profile',
  [StateStageEnum.AvailabilityRequired]: 'availability',
  [StateStageEnum.Ready]: 'complete'
};

export function toPresentationStage(state: Pick<State, 'stage'>): OnboardingPresentationStage | undefined {
  return state.stage ? presentationStages[state.stage] : undefined;
}

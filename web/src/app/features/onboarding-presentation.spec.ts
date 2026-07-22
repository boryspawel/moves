import { describe, expect, it } from 'vitest';
import { StateStageEnum } from '../api/generated/src';
import { toPresentationStage } from './onboarding-presentation';

describe('toPresentationStage', () => {
  it.each([
    [StateStageEnum.ProfileTypeRequired, 'profile-type'],
    [StateStageEnum.LegalRequired, 'legal'],
    [StateStageEnum.ProfileRequired, 'basic-profile'],
    [StateStageEnum.AvailabilityRequired, 'availability'],
    [StateStageEnum.Ready, 'complete']
  ] as const)('maps %s to %s', (stage, expected) => {
    expect(toPresentationStage({ stage })).toBe(expected);
  });
});

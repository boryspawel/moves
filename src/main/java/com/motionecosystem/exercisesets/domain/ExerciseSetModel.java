package com.motionecosystem.exercisesets.domain;

public final class ExerciseSetModel {
    private ExerciseSetModel() { }
    public enum Visibility { PRIVATE, SHARED, ORGANIZATION }
    public enum VersionStatus { DRAFT, PUBLISHED, RETIRED }
    public enum SetProfile { FULL_SELF_GUIDED, WARMUP_MODULE, MAIN_MODULE, ACCESSORY_MODULE, COOLDOWN_MODULE, HOME, THERAPEUTIC, MOBILITY, STRETCHING, BREATHING }
    public enum Phase { PREPARATION, MAIN, ACCESSORY, COOLDOWN }
    public enum VariantKind { BASE, SHORT, MINIMUM }
    public enum Side { NOT_APPLICABLE, LEFT, RIGHT, BILATERAL }
}

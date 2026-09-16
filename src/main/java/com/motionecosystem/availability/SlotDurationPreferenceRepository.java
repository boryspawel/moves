package com.motionecosystem.availability;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface SlotDurationPreferenceRepository extends JpaRepository<SlotDurationPreference, UUID> { }

package com.motionecosystem.notification.reminders;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface ReminderPreferenceRepository extends JpaRepository<ReminderPreference, UUID> { }

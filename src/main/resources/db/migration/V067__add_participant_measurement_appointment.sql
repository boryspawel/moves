ALTER TABLE participant_measurement.participant_measurement ADD COLUMN appointment_id UUID;
ALTER TABLE participant_measurement.participant_measurement ADD CONSTRAINT fk_participant_measurement_appointment FOREIGN KEY (appointment_id) REFERENCES calendar.appointment(id);
CREATE INDEX ix_participant_measurement_appointment ON participant_measurement.participant_measurement (participant_id, appointment_id, measured_at DESC, recorded_at DESC, id DESC);

package com.motionecosystem.specialist;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
interface ClientCreateIdempotencyRepository extends JpaRepository<ClientCreateIdempotency, ClientCreateIdempotency.Id> { Optional<ClientCreateIdempotency> findById(ClientCreateIdempotency.Id id); }

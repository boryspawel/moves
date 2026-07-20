package com.motionecosystem.application;

import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@Configuration(proxyBeanMethods = false)
@EntityScan(basePackages = "com.motionecosystem")
@EnableJpaRepositories(basePackages = "com.motionecosystem")
class PersistenceConfiguration {
}

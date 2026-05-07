package com.mylife.pacs.boot.config;

import com.mylife.pacs.common.config.DicomPacsProperties;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@Configuration(proxyBeanMethods = false)
@EntityScan(basePackages = "com.mylife.pacs.infrastructure.persistence.entity")
@EnableJpaRepositories(basePackages = "com.mylife.pacs.infrastructure.persistence.springdata")
@ConfigurationPropertiesScan(basePackageClasses = DicomPacsProperties.class)
public class JpaConfig {
}

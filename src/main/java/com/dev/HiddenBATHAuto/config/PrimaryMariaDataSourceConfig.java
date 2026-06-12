package com.dev.HiddenBATHAuto.config;

import java.util.HashMap;
import java.util.Map;

import javax.sql.DataSource;

import org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.orm.jpa.EntityManagerFactoryBuilder;
import org.springframework.boot.orm.jpa.hibernate.SpringImplicitNamingStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;

import com.zaxxer.hikari.HikariDataSource;

import jakarta.persistence.EntityManagerFactory;

@Configuration
@EnableJpaRepositories(
        basePackages = "com.dev.HiddenBATHAuto.repository",
        entityManagerFactoryRef = "entityManagerFactory",
        transactionManagerRef = "transactionManager"
)
public class PrimaryMariaDataSourceConfig {

    @Bean(name = "primaryDataSourceProperties")
    @Primary
    @ConfigurationProperties("spring.datasource")
    DataSourceProperties primaryDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean(name = "dataSource")
    @Primary
    @ConfigurationProperties("spring.datasource.hikari")
    HikariDataSource dataSource(
            @Qualifier("primaryDataSourceProperties") DataSourceProperties properties
    ) {
        return properties
                .initializeDataSourceBuilder()
                .type(HikariDataSource.class)
                .build();
    }

    @Bean(name = "entityManagerFactory")
    @Primary
    LocalContainerEntityManagerFactoryBean entityManagerFactory(
            EntityManagerFactoryBuilder builder,
            @Qualifier("dataSource") DataSource dataSource
    ) {
        Map<String, Object> properties = new HashMap<>();

        properties.put(
                "hibernate.physical_naming_strategy",
                CamelCaseToUnderscoresNamingStrategy.class.getName()
        );

        properties.put(
                "hibernate.implicit_naming_strategy",
                SpringImplicitNamingStrategy.class.getName()
        );

        return builder
                .dataSource(dataSource)
                .packages(
                        "com.dev.HiddenBATHAuto.model",
                        "com.dev.HiddenBATHAuto.entity"
                )
                .persistenceUnit("primary")
                .properties(properties)
                .build();
    }

    @Bean(name = "transactionManager")
    @Primary
    JpaTransactionManager transactionManager(
            @Qualifier("entityManagerFactory") EntityManagerFactory entityManagerFactory
    ) {
        return new JpaTransactionManager(entityManagerFactory);
    }
}
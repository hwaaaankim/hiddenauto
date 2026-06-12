package com.dev.HiddenBATHAuto.rag.config;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

import com.zaxxer.hikari.HikariDataSource;

@Configuration
@EnableConfigurationProperties({
        RagOpenAiProperties.class,
        RagUploadProperties.class
})
public class RagDataSourceConfig {

    @Bean(name = "ragDataSourceProperties")
    @ConfigurationProperties("hiddenbath.rag.datasource")
    DataSourceProperties ragDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean(name = "ragDataSource")
    @ConfigurationProperties("hiddenbath.rag.datasource.hikari")
    HikariDataSource ragDataSource(
            @Qualifier("ragDataSourceProperties") DataSourceProperties properties
    ) {
        return properties
                .initializeDataSourceBuilder()
                .type(HikariDataSource.class)
                .build();
    }

    @Bean(name = "ragJdbcTemplate")
    NamedParameterJdbcTemplate ragJdbcTemplate(
            @Qualifier("ragDataSource") DataSource ragDataSource
    ) {
        return new NamedParameterJdbcTemplate(ragDataSource);
    }

    @Bean(name = "ragTransactionManager")
    PlatformTransactionManager ragTransactionManager(
            @Qualifier("ragDataSource") DataSource ragDataSource
    ) {
        return new DataSourceTransactionManager(ragDataSource);
    }
}

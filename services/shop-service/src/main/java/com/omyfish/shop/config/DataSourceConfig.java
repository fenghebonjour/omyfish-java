package com.omyfish.shop.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;
import java.util.Map;

/**
 * Three DataSource beans: two leaf HikariDataSources (primary, replica) and one routing
 * composite that's what everything else (JPA, Flyway) actually autowires, via @Primary — Spring
 * Boot's own DataSourceAutoConfiguration backs off automatically once any DataSource bean
 * already exists, so no exclusion is needed.
 *
 * <p>spring.datasource-replica.* defaults to the same URL/credentials as spring.datasource.* when
 * unset, so a local run or test with no postgres-replica container just routes "replica" lookups
 * back to the same database — the routing still exists, it just has nothing separate to route to.
 */
@Configuration
class DataSourceConfig {

    @Bean
    @ConfigurationProperties(prefix = "spring.datasource.hikari")
    HikariDataSource primaryDataSource(
        @Value("${spring.datasource.url}") String url,
        @Value("${spring.datasource.username}") String username,
        @Value("${spring.datasource.password}") String password
    ) {
        return buildHikariDataSource("primary", url, username, password);
    }

    @Bean
    @ConfigurationProperties(prefix = "spring.datasource-replica.hikari")
    HikariDataSource replicaDataSource(
        @Value("${spring.datasource-replica.url}") String url,
        @Value("${spring.datasource-replica.username}") String username,
        @Value("${spring.datasource-replica.password}") String password
    ) {
        return buildHikariDataSource("replica", url, username, password);
    }

    @Bean
    @Primary
    DataSource routingDataSource(
        @Qualifier("primaryDataSource") DataSource primary, @Qualifier("replicaDataSource") DataSource replica
    ) {
        ReadWriteRoutingDataSource routing = new ReadWriteRoutingDataSource();
        routing.setTargetDataSources(Map.of(
            ReadWriteRoutingDataSource.PRIMARY, primary, ReadWriteRoutingDataSource.REPLICA, replica));
        routing.setDefaultTargetDataSource(primary);
        routing.afterPropertiesSet();
        return routing;
    }

    private static HikariDataSource buildHikariDataSource(String poolName, String url, String username, String password) {
        HikariDataSource ds = new HikariDataSource();
        ds.setPoolName(poolName);
        ds.setJdbcUrl(url);
        ds.setUsername(username);
        ds.setPassword(password);
        ds.setDriverClassName("org.postgresql.Driver");
        return ds;
    }
}

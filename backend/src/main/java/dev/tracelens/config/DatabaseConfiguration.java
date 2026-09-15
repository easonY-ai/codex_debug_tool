package dev.tracelens.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;

import javax.sql.DataSource;
import java.io.IOException;
import java.net.InetAddress;

@Configuration
@EnableConfigurationProperties(JsonlProperties.class)
public class DatabaseConfiguration {
    @Bean
    DataSource dataSource(
            @Value("${analyzer.database.url}") String url,
            @Value("${MYSQL_HOST:127.0.0.1}") String host,
            @Value("${analyzer.database.username}") String username,
            @Value("${analyzer.database.password}") String password,
            @Value("${analyzer.database.pool.maximum-size}") int maximumPoolSize,
            @Value("${analyzer.database.pool.minimum-idle}") int minimumIdle,
            @Value("${analyzer.database.pool.connection-timeout-ms}") long connectionTimeout,
            @Value("${analyzer.database.pool.socket-timeout-ms}") int socketTimeout,
            @Value("${analyzer.database.pool.connection-time-zone}") String connectionTimeZone,
            @Value("${analyzer.database.pool.connection-collation}") String connectionCollation,
            @Value("${analyzer.database.pool.session-variables}") String sessionVariables) {
        if (username.isBlank()) throw new IllegalArgumentException("MYSQL_USERNAME must not be blank");
        if (password.isBlank()) throw new IllegalArgumentException("MYSQL_PASSWORD must not be blank");
        if (!java.util.Set.of("127.0.0.1", "localhost", "::1").contains(host)) {
            throw new IllegalArgumentException("Database URL must point to a loopback MySQL instance");
        }
        HikariConfig pool = new HikariConfig();
        pool.setJdbcUrl(url);
        pool.setUsername(username);
        pool.setPassword(password);
        pool.setMaximumPoolSize(maximumPoolSize);
        pool.setMinimumIdle(minimumIdle);
        pool.setConnectionTimeout(connectionTimeout);
        pool.setPoolName("trace-lens-mysql");
        pool.setTransactionIsolation("TRANSACTION_READ_COMMITTED");
        pool.addDataSourceProperty("characterEncoding", "UTF-8");
        pool.addDataSourceProperty("connectionCollation", connectionCollation);
        pool.addDataSourceProperty("connectionTimeZone", connectionTimeZone);
        pool.addDataSourceProperty("useAffectedRows", "true");
        pool.addDataSourceProperty("connectTimeout", "5000");
        pool.addDataSourceProperty("socketTimeout", Integer.toString(socketTimeout));
        // Keep invalid/truncated values as errors, including in duplicate-key writes.
        pool.addDataSourceProperty("sessionVariables", sessionVariables);
        return pool;
    }

    @Bean
    WebServerFactoryCustomizer<TomcatServletWebServerFactory> loopbackOnly(
            @Value("${server.address}") String address) throws IOException {
        InetAddress resolved = InetAddress.getByName(address);
        if (!resolved.isLoopbackAddress()) throw new IllegalArgumentException("Only loopback binding is supported");
        return factory -> factory.setAddress(resolved);
    }
}

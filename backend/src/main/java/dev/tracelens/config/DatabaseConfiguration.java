package dev.tracelens.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.sqlite.SQLiteConfig;
import org.sqlite.SQLiteDataSource;
import org.mybatis.spring.boot.autoconfigure.ConfigurationCustomizer;
import org.apache.ibatis.type.ByteArrayTypeHandler;
import org.apache.ibatis.type.JdbcType;

import javax.sql.DataSource;
import java.io.IOException;
import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Path;

@Configuration
@EnableConfigurationProperties(JsonlProperties.class)
public class DatabaseConfiguration {
    @Bean
    ConfigurationCustomizer sqliteBytes() {
        // SQLite supports getBytes, but not the JDBC getBlob API used by MyBatis' default BLOB handler.
        return configuration -> configuration.getTypeHandlerRegistry()
                .register(byte[].class, JdbcType.BLOB, new ByteArrayTypeHandler());
    }

    @Bean
    DataSource dataSource(@Value("${analyzer.database}") String filename) throws IOException {
        Path path = Path.of(filename).toAbsolutePath().normalize();
        Files.createDirectories(path.getParent());
        SQLiteConfig sqlite = new SQLiteConfig();
        sqlite.setJournalMode(SQLiteConfig.JournalMode.WAL);
        sqlite.enforceForeignKeys(true);
        sqlite.setBusyTimeout(5000);
        SQLiteDataSource source = new SQLiteDataSource(sqlite);
        source.setUrl("jdbc:sqlite:" + path);
        HikariConfig pool = new HikariConfig();
        pool.setDataSource(source);
        pool.setMaximumPoolSize(1);
        pool.setMinimumIdle(1);
        pool.setPoolName("trace-lens-sqlite");
        return new HikariDataSource(pool);
    }

    @Bean
    WebServerFactoryCustomizer<TomcatServletWebServerFactory> loopbackOnly(
            @Value("${server.address}") String address) throws IOException {
        InetAddress resolved = InetAddress.getByName(address);
        if (!resolved.isLoopbackAddress()) throw new IllegalArgumentException("Only loopback binding is supported");
        return factory -> factory.setAddress(resolved);
    }
}

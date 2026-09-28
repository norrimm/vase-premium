package com.example.trade;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class TradeApplication {

    /** application.yml の spring.datasource.url と同じ既定値。 */
    public static final String DEFAULT_DB_PATH = "../data/trade.db";

    public static void main(String[] args) throws IOException {
        // SQLite はファイルは作るが親ディレクトリは作らないので、起動前に用意しておく
        String dbPath = System.getenv().getOrDefault("DB_PATH", DEFAULT_DB_PATH);
        Path parent = Path.of(dbPath).toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        SpringApplication.run(TradeApplication.class, args);
    }
}

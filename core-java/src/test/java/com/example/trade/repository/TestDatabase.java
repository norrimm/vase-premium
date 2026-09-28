package com.example.trade.repository;

import java.nio.file.Path;

import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

/** 一時ディレクトリに schema.sql を流した SQLite を作る。 */
public final class TestDatabase {

    private TestDatabase() {
    }

    /** テスト後に destroy() で閉じること（Windows では開いたままだとファイルを消せない）。 */
    public static SingleConnectionDataSource create(Path dir) {
        SingleConnectionDataSource ds = new SingleConnectionDataSource(
                "jdbc:sqlite:" + dir.resolve("test.db"), true);
        new ResourceDatabasePopulator(new ClassPathResource("schema.sql")).execute(ds);
        return ds;
    }
}

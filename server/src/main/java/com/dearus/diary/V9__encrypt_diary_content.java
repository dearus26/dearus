package com.dearus.diary;

import com.dearus.common.CryptoService;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/**
 * 일기 본문을 암호화해 저장하도록 바꾸는 마이그레이션이에요(2026.11.18 결정).
 * diaries.content(평문)를 content_enc(AES-256-GCM, CryptoService와 같은 형식)로 옮기고 평문 컬럼을 지워요.
 * 키(ENCRYPTION_KEY)가 서버에만 있어서 SQL 파일로는 못 하고, 스프링 빈으로 만들어 Flyway가 V8 다음에 실행해요.
 * 한 트랜잭션에서 돌아서 중간에 실패하면 처음 상태로 돌아가요.
 * 글자 수 검사(1~5000자)는 DB에서 할 수 없게 되어 API의 요청 검사(@Size)만 남아요.
 */
@Component
public class V9__encrypt_diary_content extends BaseJavaMigration {

	private static final int BATCH = 200;

	private final CryptoService crypto;

	public V9__encrypt_diary_content(CryptoService crypto) {
		this.crypto = crypto;
	}

	@Override
	public void migrate(Context context) throws Exception {
		Connection connection = context.getConnection();
		try (Statement ddl = connection.createStatement()) {
			ddl.execute("ALTER TABLE diaries ADD COLUMN content_enc bytea");
		}
		try (Statement select = connection.createStatement();
			ResultSet rows = select.executeQuery("SELECT id, content FROM diaries");
			PreparedStatement update = connection.prepareStatement("UPDATE diaries SET content_enc = ? WHERE id = ?")) {
			int pending = 0;
			while (rows.next()) {
				update.setBytes(1, crypto.encrypt(rows.getString("content")));
				update.setObject(2, rows.getObject("id"));
				update.addBatch();
				if (++pending == BATCH) {
					update.executeBatch();
					pending = 0;
				}
			}
			if (pending > 0) {
				update.executeBatch();
			}
		}
		try (Statement ddl = connection.createStatement()) {
			ddl.execute("ALTER TABLE diaries ALTER COLUMN content_enc SET NOT NULL");
			ddl.execute("ALTER TABLE diaries DROP CONSTRAINT ck_diaries_content_length");
			ddl.execute("ALTER TABLE diaries DROP COLUMN content");
		}
	}
}

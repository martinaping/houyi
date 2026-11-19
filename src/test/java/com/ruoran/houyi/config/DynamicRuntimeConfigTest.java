package com.ruoran.houyi.config;

import com.ruoran.houyi.constants.AppConstants;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 运行参数热更新：读取、越界收敛、非法值与表缺失时的回退。
 */
class DynamicRuntimeConfigTest {

    @Test
    void appliesValuesAndClampsOutOfRange() throws SQLException {
        DynamicRuntimeConfig config = configWith(Map.of(
                "runtime.message-interval-ms", "1000",
                "runtime.corp-refresh-seconds", "99999",
                "runtime.download-max-retry", "5",
                "runtime.big-file-timeout-ms", "60000",
                "runtime.download-core-size", "0",
                "runtime.download-max-size", "999",
                "runtime.oss-core-size", "6",
                "runtime.oss-max-size", "80"));

        config.refresh();

        assertThat(config.getMessageIntervalMs()).isEqualTo(1000);
        assertThat(config.getCorpRefreshSeconds()).isEqualTo(3600);
        assertThat(config.getDownloadMaxRetry()).isEqualTo(5);
        assertThat(config.getBigFileTimeoutMs()).isEqualTo(60000);
        assertThat(config.getDownloadCoreSize()).isEqualTo(1);
        assertThat(config.getDownloadMaxSize()).isEqualTo(500);
        assertThat(config.getOssCoreSize()).isEqualTo(6);
        assertThat(config.getOssMaxSize()).isEqualTo(80);
        assertThat(AppConstants.Retry.MAX_TRY_COUNT).isEqualTo(5);
        assertThat(AppConstants.Retry.BIG_FILE_TIMEOUT_MS).isEqualTo(60000);
    }

    @Test
    void keepsDefaultsWhenTableMissing() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        doThrow(new BadSqlGrammarException("query", "SELECT 1", new SQLException("table not found")))
                .when(jdbcTemplate).query(anyString(), any(RowCallbackHandler.class));

        DynamicRuntimeConfig config = new DynamicRuntimeConfig(jdbcTemplate);
        config.refresh();

        assertThat(config.getMessageIntervalMs()).isEqualTo(200);
        assertThat(config.getCorpRefreshSeconds()).isEqualTo(30);
        assertThat(config.getDownloadMaxRetry()).isEqualTo(16);
        assertThat(config.getBigFileTimeoutMs()).isEqualTo(300_000L);
    }

    @Test
    void fallsBackWhenValueNotNumeric() throws SQLException {
        DynamicRuntimeConfig config = configWith(Map.of(
                "runtime.message-interval-ms", "abc",
                "runtime.download-max-retry", "8"));

        config.refresh();

        assertThat(config.getMessageIntervalMs()).isEqualTo(200);
        assertThat(config.getDownloadMaxRetry()).isEqualTo(8);
    }

    private DynamicRuntimeConfig configWith(Map<String, String> rows) throws SQLException {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        doAnswer(invocation -> {
            RowCallbackHandler handler = invocation.getArgument(1);
            for (Map.Entry<String, String> entry : new LinkedHashMap<>(rows).entrySet()) {
                ResultSet rs = mock(ResultSet.class);
                when(rs.getString("config_key")).thenReturn(entry.getKey());
                when(rs.getString("config_value")).thenReturn(entry.getValue());
                handler.processRow(rs);
            }
            return null;
        }).when(jdbcTemplate).query(anyString(), any(RowCallbackHandler.class));
        return new DynamicRuntimeConfig(jdbcTemplate);
    }
}

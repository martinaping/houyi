package com.ruoran.houyi.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 调试接口会直接序列化 CorpInfo，密钥与私钥不能出现在 JSON 或日志中。
 */
class CorpInfoJsonTest {

    @Test
    void secretsAreExcludedFromJsonAndToString() throws Exception {
        CorpInfo info = new CorpInfo();
        info.setCorpid("ww-test");
        info.setCorpname("测试企业");
        info.setSecret("secret-value");
        info.setPrikey("private-key-value");

        String json = new ObjectMapper().writeValueAsString(info);
        assertTrue(json.contains("ww-test"));
        assertFalse(json.contains("secret-value"));
        assertFalse(json.contains("private-key-value"));

        String text = info.toString();
        assertFalse(text.contains("secret-value"));
        assertFalse(text.contains("private-key-value"));
    }
}

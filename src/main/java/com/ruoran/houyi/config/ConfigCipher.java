package com.ruoran.houyi.config;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

/**
 * 与管理端一致的敏感配置解密工具。
 *
 * <p>密文由管理端使用环境变量 {@code HOUYI_CONFIG_KEY} 派生密钥加密，
 * 主应用在启动期读取数据库配置时用同一主密钥解密。</p>
 */
public final class ConfigCipher {

    public static final String ENV_KEY = "HOUYI_CONFIG_KEY";
    private static final String PREFIX = "enc:v1:";
    private static final int IV_LENGTH = 12;
    private static final int TAG_BITS = 128;

    private final SecretKeySpec key;

    public ConfigCipher(String masterKey) {
        this.key = masterKey == null || masterKey.isBlank() ? null : derive(masterKey);
    }

    public boolean available() {
        return key != null;
    }

    public static boolean isEncrypted(String value) {
        return value != null && value.startsWith(PREFIX);
    }

    public String decrypt(String value) {
        if (!isEncrypted(value)) {
            return value;
        }
        if (key == null) {
            throw new IllegalStateException("缺少环境变量 " + ENV_KEY + "，无法解密敏感配置");
        }
        try {
            byte[] payload = Base64.getDecoder().decode(value.substring(PREFIX.length()));
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, payload, 0, IV_LENGTH));
            byte[] plain = cipher.doFinal(payload, IV_LENGTH, payload.length - IV_LENGTH);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (Exception ex) {
            throw new IllegalStateException("敏感配置解密失败，请确认 " + ENV_KEY + " 未变更: "
                    + ex.getMessage(), ex);
        }
    }

    private static SecretKeySpec derive(String raw) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(raw.getBytes(StandardCharsets.UTF_8));
            return new SecretKeySpec(digest, "AES");
        } catch (Exception ex) {
            throw new IllegalStateException("派生配置主密钥失败: " + ex.getMessage(), ex);
        }
    }
}

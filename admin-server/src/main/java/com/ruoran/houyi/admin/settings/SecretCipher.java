package com.ruoran.houyi.admin.settings;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 敏感配置的对称加解密。
 *
 * <p>主密钥来自环境变量 {@code HOUYI_CONFIG_KEY}，经 SHA-256 派生为 256 位 AES 密钥，
 * 采用 AES/GCM/NoPadding 加密，密文格式为 {@code enc:v1:<base64(iv||cipher)>}。
 * 主密钥缺失时 {@link #available()} 返回 false，调用方应拒绝写入敏感值。</p>
 */
@Component
public class SecretCipher {

    private static final Logger log = LoggerFactory.getLogger(SecretCipher.class);

    public static final String ENV_KEY = "HOUYI_CONFIG_KEY";
    public static final String PREFIX = "enc:v1:";

    private static final int IV_LENGTH = 12;
    private static final int TAG_BITS = 128;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final SecretKeySpec key;

    public SecretCipher() {
        String raw = System.getenv(ENV_KEY);
        this.key = raw == null || raw.isBlank() ? null : derive(raw);
        if (key == null) {
            log.warn("未设置环境变量 {}，管理端敏感配置将不可写入", ENV_KEY);
        }
    }

    public boolean available() {
        return key != null;
    }

    public static boolean isEncrypted(String value) {
        return value != null && value.startsWith(PREFIX);
    }

    public String encrypt(String plain) {
        if (plain == null || plain.isEmpty()) {
            return "";
        }
        if (key == null) {
            throw new IllegalStateException("缺少环境变量 " + ENV_KEY + "，无法加密敏感配置");
        }
        try {
            byte[] iv = new byte[IV_LENGTH];
            RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] encrypted = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            byte[] payload = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, payload, 0, iv.length);
            System.arraycopy(encrypted, 0, payload, iv.length, encrypted.length);
            return PREFIX + Base64.getEncoder().encodeToString(payload);
        } catch (Exception ex) {
            throw new IllegalStateException("敏感配置加密失败: " + ex.getMessage(), ex);
        }
    }

    /**
     * 解密。非密文原样返回（兼容历史明文数据）。
     */
    public String decrypt(String value) {
        if (!isEncrypted(value)) {
            return value;
        }
        if (key == null) {
            throw new IllegalStateException("缺少环境变量 " + ENV_KEY + "，无法解密敏感配置");
        }
        try {
            byte[] payload = Base64.getDecoder().decode(value.substring(PREFIX.length()));
            if (payload.length <= IV_LENGTH) {
                throw new IllegalArgumentException("密文长度不合法");
            }
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

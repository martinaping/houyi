package com.ruoran.houyi.config;

import com.aliyun.oss.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

/**
 * @author renlu
 * created by renlu at 2021/7/15 2:24 下午
 */
@Configuration
@Slf4j
public class OssClientConfig {
    @Value("${aliyun.oss_end_point}")
    String ossEndPoint;
    @Value("${aliyun.oss_access_key}")
    String ossAccessKey;
    @Value("${aliyun.oss_access_secret}")
    String ossAccessSecret;
    @Bean
    public OSS ossClient(){
        ClientBuilderConfiguration configuration = new ClientBuilderConfiguration();
        configuration.setConnectionTimeout(3000);
        configuration.setMaxErrorRetry(3);
        log.info("初始化 OSS 客户端: endpoint={}, key={}, secret={}",
            ossEndPoint,
            maskSensitiveInfo(ossAccessKey),
            maskSensitiveInfo(ossAccessSecret));
        return new OSSClientBuilder().build(ossEndPoint, ossAccessKey, ossAccessSecret,configuration);
    }
    
    /**
     * 掩码敏感信息，只显示前4个字符，其余用***替代
     */
    private String maskSensitiveInfo(String value) {
        if (!StringUtils.hasText(value)) {
            return "(空)";
        }
        if (value.length() <= 4) {
            return "***";
        }
        return value.substring(0, 4) + "***";
    }
}

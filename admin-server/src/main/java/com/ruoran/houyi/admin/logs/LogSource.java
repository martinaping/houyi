package com.ruoran.houyi.admin.logs;

import java.nio.file.Path;
import java.util.List;

/**
 * 日志来源定义：一组允许读取的目录 + 文件名匹配规则。
 */
public record LogSource(String key, String name, String description,
                        List<Path> dirs, String pattern) {
}

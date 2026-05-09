package com.yiming.aiagentproject.ai.imagecollection;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 占位符兜底安全网：把残留的 {@code __IMG_SLOT_xxx__} 强制替换为可用的兜底图 URL，
 * 杜绝 {@code __IMG_SLOT_*__} 字符串泄露到生产页面。
 *
 * <p>规则：
 * <ul>
 *     <li>同一 slotId 在多处出现时返回同一张兜底图（基于 slotId 哈希派生 picsum seed）。</li>
 *     <li>命中数返回值，便于上层打 {@code placeholderLeakCount} 日志。</li>
 *     <li>{@link #scrubFile(File)} 跳过二进制 / 超大文件，避免误改。</li>
 * </ul>
 */
@Slf4j
public final class PlaceholderFallbackScrubber {

    /** 占位符正则：匹配 {@code __IMG_SLOT_xxx__}，xxx 仅允许小写字母数字下划线。 */
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("__IMG_SLOT_([a-z0-9_]+)__");

    /** 单文件 scrub 大小上限（4MB）；超过该尺寸视为大文件，直接跳过。 */
    private static final long MAX_SCRUB_FILE_SIZE = 4L * 1024 * 1024;

    /** 兜底图基础尺寸（picsum 默认 16:9 近似比例）。 */
    private static final int FALLBACK_IMG_WIDTH = 1200;
    private static final int FALLBACK_IMG_HEIGHT = 675;

    private PlaceholderFallbackScrubber() {
    }

    /** scrub 操作的结果：替换后内容 + 命中次数。 */
    public record ScrubResult(String content, int placeholderLeakCount) {
    }

    /**
     * scrub 字符串：把所有 {@code __IMG_SLOT_xxx__} 替换为兜底 URL。
     * 入参为 null/空字符串时直接返回零命中。
     */
    public static ScrubResult scrub(String content) {
        if (StrUtil.isEmpty(content)) {
            return new ScrubResult(content, 0);
        }
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(content);
        StringBuffer sb = new StringBuffer(content.length());
        int hit = 0;
        while (matcher.find()) {
            String slotId = matcher.group(1);
            String fallbackUrl = buildFallbackUrl(slotId);
            matcher.appendReplacement(sb, Matcher.quoteReplacement(fallbackUrl));
            hit++;
        }
        if (hit == 0) {
            return new ScrubResult(content, 0);
        }
        matcher.appendTail(sb);
        return new ScrubResult(sb.toString(), hit);
    }

    /**
     * scrub 单个文件：读取 → scrub → 回写；二进制 / 超大文件跳过。
     * 失败仅记录日志，不抛异常。
     */
    public static int scrubFile(File file) {
        if (file == null || !file.exists() || !file.isFile()) {
            return 0;
        }
        if (file.length() > MAX_SCRUB_FILE_SIZE) {
            log.debug("scrubFile 跳过超大文件: {} ({} bytes)", file.getAbsolutePath(), file.length());
            return 0;
        }
        try {
            String content = FileUtil.readString(file, StandardCharsets.UTF_8);
            ScrubResult result = scrub(content);
            if (result.placeholderLeakCount() > 0) {
                FileUtil.writeString(result.content(), file, StandardCharsets.UTF_8);
                log.info("scrubFile 命中并兜底 {} 处占位符: {}", result.placeholderLeakCount(), file.getAbsolutePath());
            }
            return result.placeholderLeakCount();
        } catch (Exception e) {
            log.warn("scrubFile 失败，跳过该文件: {} ({})", file.getAbsolutePath(), e.getMessage());
            return 0;
        }
    }

    /**
     * 递归扫描目录，对指定后缀的文件执行 scrubFile。
     *
     * @param root       根目录
     * @param extensions 允许的小写后缀（不带点），例如 Set.of("vue", "js", "ts", "css", "html")
     * @return 总命中数
     */
    public static int scrubDirectory(File root, Set<String> extensions) {
        if (root == null || !root.exists() || !root.isDirectory() || extensions == null || extensions.isEmpty()) {
            return 0;
        }
        int[] total = {0};
        try {
            FileUtil.walkFiles(root, file -> {
                if (file == null || !file.isFile()) {
                    return;
                }
                String name = file.getName();
                int dot = name.lastIndexOf('.');
                if (dot < 0 || dot == name.length() - 1) {
                    return;
                }
                String ext = name.substring(dot + 1).toLowerCase();
                if (!extensions.contains(ext)) {
                    return;
                }
                total[0] += scrubFile(file);
            });
        } catch (Exception e) {
            log.warn("scrubDirectory 失败: {} ({})", root.getAbsolutePath(), e.getMessage());
        }
        return total[0];
    }

    /**
     * 基于 slotId 哈希派生稳定的兜底 URL（picsum 的 seed 模式保证同一 slotId 总是同一张图）。
     */
    public static String buildFallbackUrl(String slotId) {
        String seed = StrUtil.isEmpty(slotId) ? "img_slot" : slotId;
        return "https://picsum.photos/seed/" + seed + "/" + FALLBACK_IMG_WIDTH + "/" + FALLBACK_IMG_HEIGHT;
    }
}

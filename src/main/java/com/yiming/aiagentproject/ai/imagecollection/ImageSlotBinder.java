package com.yiming.aiagentproject.ai.imagecollection;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.yiming.aiagentproject.langgraph4j.model.ImageResource;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 图片槽位绑定器：把 {@code __IMG_SLOT_*__} 占位符替换为对应 slotId 的真实素材 URL。
 *
 * <p>设计要点：
 * <ul>
 *     <li>仅做"命中真实素材"的替换；未命中的 slotId 不在这里兜底，
 *         交给 {@link PlaceholderFallbackScrubber} 在保存前那一道网处理。</li>
 *     <li>覆盖统一的占位符正则即可：替换发生在占位符字符上，
 *         {@code src=""} / {@code :src=""} / {@code url(...)} 三种语法的引号原样保留。</li>
 *     <li>同一 slotId 在多处出现时被替换为同一张真实图。</li>
 * </ul>
 */
@Slf4j
public final class ImageSlotBinder {

    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("__IMG_SLOT_([a-z0-9_]+)__");

    private ImageSlotBinder() {
    }

    /** 绑定结果：替换后内容 + 命中数(命中真实素材的次数)。 */
    public record BindResult(String content, int hitCount) {
    }

    /**
     * 用真实素材替换占位符。
     *
     * @param code   待绑定的代码字符串
     * @param images 真实素材列表（每条必须带 {@code slotId}，否则被忽略）
     * @return 绑定后的代码 + 命中数
     */
    public static BindResult bind(String code, List<ImageResource> images) {
        if (StrUtil.isEmpty(code)) {
            return new BindResult(code, 0);
        }
        Map<String, String> slotToUrl = buildSlotUrlMap(images);
        if (slotToUrl.isEmpty()) {
            return new BindResult(code, 0);
        }
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(code);
        StringBuffer sb = new StringBuffer(code.length());
        int hit = 0;
        while (matcher.find()) {
            String slotId = matcher.group(1);
            String realUrl = slotToUrl.get(slotId);
            if (StrUtil.isNotEmpty(realUrl)) {
                matcher.appendReplacement(sb, Matcher.quoteReplacement(realUrl));
                hit++;
            } else {
                // 未命中：保留占位符原样，交给 scrubber 处理
                matcher.appendReplacement(sb, Matcher.quoteReplacement(matcher.group()));
            }
        }
        matcher.appendTail(sb);
        if (hit > 0) {
            log.info("ImageSlotBinder 绑定 {} 处真实素材", hit);
        }
        return new BindResult(sb.toString(), hit);
    }

    /**
     * 构建 slotId → 真实 URL 的映射。
     * 同一 slotId 多条素材时取首个非空 URL，保证多处引用一致性。
     */
    private static Map<String, String> buildSlotUrlMap(List<ImageResource> images) {
        Map<String, String> map = new HashMap<>();
        if (CollUtil.isEmpty(images)) {
            return map;
        }
        for (ImageResource image : images) {
            if (image == null) {
                continue;
            }
            String slotId = image.getSlotId();
            String url = image.getUrl();
            if (StrUtil.isEmpty(slotId) || StrUtil.isEmpty(url)) {
                continue;
            }
            map.putIfAbsent(slotId, url);
        }
        return map;
    }
}

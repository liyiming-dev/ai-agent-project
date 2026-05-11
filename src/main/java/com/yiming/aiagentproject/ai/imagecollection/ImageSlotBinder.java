package com.yiming.aiagentproject.ai.imagecollection;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.yiming.aiagentproject.langgraph4j.model.ImageResource;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 图片槽位绑定器：把 {@code __IMG_SLOT_*__} 占位符替换为对应 slotId 的真实素材 URL。
 *
 * <p>设计要点（2026-05-11 重写：从"slot → 1 URL"改为"slot → URL 列表 + 出现次数轮询 + 全局未用池兜底"）：
 * <ul>
 *     <li>每个 slot 收集到的多张图按收集顺序保留为列表，模型在多处复用同一 slotId 时，
 *         按出现次数从列表里挑不同图，避免"showcase 三宫格全是同一张"。</li>
 *     <li>替换优先级：① slot 自身未用过的图 → ② 全局未用过的图 → ③ slot 自身轮询 → ④ 全局轮询。
 *         前两步保证零重复，后两步保证"宁可重复也不退回 picsum"。</li>
 *     <li>模型自创、计划里没有的 slotId（如 {@code gallery_decor_1}）也能命中全局未用池，
 *         不再无脑交给 {@link PlaceholderFallbackScrubber} 替成 picsum，从而把 60 张采集图的利用率拉上去。</li>
 *     <li>未匹配到任何 URL（采集池全空）的占位符仍保留原样，交给 scrubber 兜底。</li>
 * </ul>
 */
@Slf4j
public final class ImageSlotBinder {

    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("__IMG_SLOT_([a-z0-9_]+)__");

    private ImageSlotBinder() {
    }

    /** 绑定结果：替换后内容 + 命中数（命中真实素材的次数）。 */
    public record BindResult(String content, int hitCount) {
    }

    /**
     * 用真实素材替换占位符。
     *
     * @param code   待绑定的代码字符串
     * @param images 真实素材列表（每条建议带 {@code slotId}；不带 slotId 的也会进入全局兜底池）
     * @return 绑定后的代码 + 命中数
     */
    public static BindResult bind(String code, List<ImageResource> images) {
        if (StrUtil.isEmpty(code)) {
            return new BindResult(code, 0);
        }
        if (CollUtil.isEmpty(images)) {
            return new BindResult(code, 0);
        }
        Map<String, List<String>> slotToUrls = new LinkedHashMap<>();
        List<String> globalPool = new ArrayList<>();
        Set<String> seenAnywhere = new HashSet<>();
        for (ImageResource image : images) {
            if (image == null) {
                continue;
            }
            String url = image.getUrl();
            if (StrUtil.isEmpty(url)) {
                continue;
            }
            if (seenAnywhere.add(url)) {
                globalPool.add(url);
            }
            String slotId = image.getSlotId();
            if (StrUtil.isNotEmpty(slotId)) {
                slotToUrls.computeIfAbsent(slotId, k -> new ArrayList<>()).add(url);
            }
        }
        if (globalPool.isEmpty()) {
            return new BindResult(code, 0);
        }

        Set<String> usedUrls = new HashSet<>();
        Map<String, Integer> slotOccurrence = new HashMap<>();
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(code);
        StringBuffer sb = new StringBuffer(code.length());
        int hit = 0;
        int recycledFromPool = 0;
        while (matcher.find()) {
            String slotId = matcher.group(1);
            int occ = slotOccurrence.merge(slotId, 1, Integer::sum) - 1;
            String chosen = pickUrl(slotId, occ, slotToUrls, globalPool, usedUrls);
            if (chosen != null) {
                matcher.appendReplacement(sb, Matcher.quoteReplacement(chosen));
                usedUrls.add(chosen);
                hit++;
                if (!slotToUrls.containsKey(slotId)) {
                    recycledFromPool++;
                }
            } else {
                matcher.appendReplacement(sb, Matcher.quoteReplacement(matcher.group()));
            }
        }
        matcher.appendTail(sb);
        if (hit > 0) {
            log.info("ImageSlotBinder 绑定 {} 处真实素材（其中 {} 处来自全局未用池兜底），采集图利用率 {}/{}",
                    hit, recycledFromPool, usedUrls.size(), globalPool.size());
        }
        return new BindResult(sb.toString(), hit);
    }

    /**
     * 替换优先级链：① slot 自身未用 → ② 全局未用 → ③ slot 自身轮询 → ④ 全局轮询。
     * 前两步保证零重复，后两步保证"宁可重复也不退回 picsum"。
     */
    private static String pickUrl(String slotId,
                                  int occurrence,
                                  Map<String, List<String>> slotToUrls,
                                  List<String> globalPool,
                                  Set<String> usedUrls) {
        List<String> ownUrls = slotToUrls.get(slotId);
        if (ownUrls != null) {
            for (String url : ownUrls) {
                if (!usedUrls.contains(url)) {
                    return url;
                }
            }
        }
        for (String url : globalPool) {
            if (!usedUrls.contains(url)) {
                return url;
            }
        }
        if (ownUrls != null && !ownUrls.isEmpty()) {
            return ownUrls.get(occurrence % ownUrls.size());
        }
        if (!globalPool.isEmpty()) {
            return globalPool.get(occurrence % globalPool.size());
        }
        return null;
    }
}

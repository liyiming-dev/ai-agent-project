package com.yiming.aiagentproject.langgraph4j.tool;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.yiming.aiagentproject.langgraph4j.enums.ImageCategoryEnum;
import com.yiming.aiagentproject.langgraph4j.model.ImageResource;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
public class UndrawIllustrationTool {

    private static final String UNDRAW_SEARCH_HOST = "undraw.co";
    private static final int SEARCH_COUNT = 12;
    private static final int TIMEOUT_MILLIS = 10000;
    private static final Pattern NEXT_DATA_PATTERN = Pattern.compile(
            "<script[^>]*id=[\"']__NEXT_DATA__[\"'][^>]*>(.*?)</script>",
            Pattern.DOTALL
    );

    @Tool("搜索插画图片，用于网站美化和装饰")
    public List<ImageResource> searchIllustrations(@P("搜索关键词") String query) {
        List<ImageResource> imageList = new ArrayList<>();
        if (StrUtil.isBlank(query)) {
            return imageList;
        }

        try {
            JSONObject pageProps = extractPageProps(fetchSearchPage(query));
            if (pageProps == null) {
                return imageList;
            }
            JSONArray initialResults = pageProps.getJSONArray("initialResults");
            if (initialResults == null || initialResults.isEmpty()) {
                return imageList;
            }
            int actualCount = Math.min(SEARCH_COUNT, initialResults.size());
            for (int i = 0; i < actualCount; i++) {
                JSONObject illustration = initialResults.getJSONObject(i);
                String title = illustration.getStr("title", "插画");
                String media = illustration.getStr("media", "");
                if (StrUtil.isNotBlank(media)) {
                    imageList.add(ImageResource.builder()
                            .category(ImageCategoryEnum.ILLUSTRATION)
                            .description(title)
                            .url(media)
                            .build());
                }
            }
        } catch (Exception e) {
            log.error("搜索插画失败：{}", e.getMessage(), e);
        }
        return imageList;
    }

    protected String fetchSearchPage(String query) {
        try (HttpResponse response = HttpRequest.get(buildSearchUrl(query))
                .timeout(TIMEOUT_MILLIS)
                .execute()) {
            if (!response.isOk()) {
                return "";
            }
            return response.body();
        }
    }

    private String buildSearchUrl(String query) {
        try {
            return new URI("https", UNDRAW_SEARCH_HOST, "/search/" + query.trim(), null).toASCIIString();
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("搜索关键词不合法：" + query, e);
        }
    }

    private JSONObject extractPageProps(String responseBody) {
        if (StrUtil.isBlank(responseBody)) {
            return null;
        }
        String json = responseBody.trim();
        if (!json.startsWith("{")) {
            Matcher matcher = NEXT_DATA_PATTERN.matcher(responseBody);
            if (!matcher.find()) {
                return null;
            }
            json = matcher.group(1);
        }
        JSONObject result = JSONUtil.parseObj(json);
        JSONObject pageProps = result.getJSONObject("pageProps");
        if (pageProps != null) {
            return pageProps;
        }
        JSONObject props = result.getJSONObject("props");
        return props == null ? null : props.getJSONObject("pageProps");
    }
}

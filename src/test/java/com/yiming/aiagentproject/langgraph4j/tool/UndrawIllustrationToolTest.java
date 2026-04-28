package com.yiming.aiagentproject.langgraph4j.tool;

import com.yiming.aiagentproject.langgraph4j.enums.ImageCategoryEnum;
import com.yiming.aiagentproject.langgraph4j.model.ImageResource;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class UndrawIllustrationToolTest {

    private static final String UNDRAW_SEARCH_HTML = """
            <!DOCTYPE html>
            <html>
            <body>
            <script id="__NEXT_DATA__" type="application/json">
            {"props":{"pageProps":{"initialResults":[
              {"title":"Happy","media":"https://cdn.undraw.co/illustration/happy_fsrv.svg"},
              {"title":"No media","media":""}
            ]}}}
            </script>
            </body>
            </html>
            """;

    @Test
    void testSearchIllustrations() {
        UndrawIllustrationTool undrawIllustrationTool = new UndrawIllustrationTool() {
            @Override
            protected String fetchSearchPage(String query) {
                return UNDRAW_SEARCH_HTML;
            }
        };

        List<ImageResource> illustrations = undrawIllustrationTool.searchIllustrations("happy");

        assertNotNull(illustrations);
        assertFalse(illustrations.isEmpty());
        ImageResource firstIllustration = illustrations.get(0);
        assertEquals(ImageCategoryEnum.ILLUSTRATION, firstIllustration.getCategory());
        assertEquals("Happy", firstIllustration.getDescription());
        assertEquals("https://cdn.undraw.co/illustration/happy_fsrv.svg", firstIllustration.getUrl());
        assertTrue(firstIllustration.getUrl().startsWith("http"));
    }

    @Test
    void testSearchIllustrationsReturnsEmptyWhenResultMissing() {
        UndrawIllustrationTool undrawIllustrationTool = new UndrawIllustrationTool() {
            @Override
            protected String fetchSearchPage(String query) {
                return "<html></html>";
            }
        };

        List<ImageResource> illustrations = undrawIllustrationTool.searchIllustrations("unknown");

        assertNotNull(illustrations);
        assertTrue(illustrations.isEmpty());
    }
}

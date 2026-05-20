package com.yiming.aiagentproject.service;

import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.service.IService;
import com.yiming.aiagentproject.dto.app.AppAddDto;
import com.yiming.aiagentproject.dto.app.AppQueryDto;
import com.yiming.aiagentproject.model.entity.App;
import com.yiming.aiagentproject.model.entity.User;
import com.yiming.aiagentproject.vo.AppVO;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * 应用 服务层。
 *
 * @author yiming
 */
public interface AppService extends IService<App> {

    Long createApp(AppAddDto appAddDto, User loginUser);

    /**
     * 获取应用视图对象
     */
    AppVO getAppVO(App app);

    /**
     * 获取应用视图对象列表
     */
    List<AppVO> getAppVOList(List<App> appList);

    /**
     * 构造查询条件
     */
    QueryWrapper getQueryWrapper(AppQueryDto appQueryDto);

    Flux<String> chatToGenCode(Long appId, String message, User loginUser);

    String deployApp(Long appId, User loginUser);

    void generateAppScreenshotAsync(Long appId, String appUrl);

    /**
     * 新开一个对话会话:刷新 app.currentSessionId,清掉该 appId 的 AiCodeGeneratorService 缓存,
     * 让下一次 chatToGenCode 重建 ChatMemory(走新 sessionId 过滤,即空历史)。
     */
    Long startNewConversation(Long appId, User loginUser);
}

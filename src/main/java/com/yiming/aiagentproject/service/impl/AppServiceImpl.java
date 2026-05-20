package com.yiming.aiagentproject.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import com.yiming.aiagentproject.ai.AiCodeGeneratorServiceFactory;
import com.yiming.aiagentproject.ai.CodeGenTypeResolver;
import com.yiming.aiagentproject.ai.guardrail.PromptSafetyInputGuardrail;
import com.yiming.aiagentproject.ai.model.enums.ChatHistoryMessageTypeEnum;
import com.yiming.aiagentproject.ai.model.enums.CodeGenTypeEnum;
import com.yiming.aiagentproject.constant.AppConstant;
import com.yiming.aiagentproject.core.AiCodeGeneratorFacade;
import com.yiming.aiagentproject.core.builder.VueProjectBuilder;
import com.yiming.aiagentproject.core.streamHandler.StreamHandlerExecutor;
import com.yiming.aiagentproject.dto.app.AppAddDto;
import com.yiming.aiagentproject.dto.app.AppQueryDto;
import com.yiming.aiagentproject.exception.BusinessException;
import com.yiming.aiagentproject.exception.ErrorCode;
import com.yiming.aiagentproject.exception.ThrowUtils;
import com.yiming.aiagentproject.mapper.AppMapper;
import com.yiming.aiagentproject.model.entity.App;
import com.yiming.aiagentproject.model.entity.User;
import com.yiming.aiagentproject.service.AppService;
import com.yiming.aiagentproject.service.ChatHistoryService;
import com.yiming.aiagentproject.service.ScreenshotService;
import com.yiming.aiagentproject.service.UserService;
import com.yiming.aiagentproject.vo.AppVO;
import com.yiming.aiagentproject.vo.UserVO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.io.File;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 应用 服务层实现。
 *
 * @author yiming
 */
@Service
@Slf4j
public class AppServiceImpl extends ServiceImpl<AppMapper, App> implements AppService {

    @Resource
    private UserService userService;
    @Resource
    private AiCodeGeneratorFacade aiCodeGeneratorFacade;
    @Resource
    private ChatHistoryService chatHistoryService;

    @Resource
    private StreamHandlerExecutor streamHandlerExecutor;
    @Resource
    private VueProjectBuilder vueProjectBuilder;
    @Resource
    private ScreenshotService screenshotService;

    @Resource
    private CodeGenTypeResolver codeGenTypeResolver;

    @Resource
    private AiCodeGeneratorServiceFactory aiCodeGeneratorServiceFactory;

    @Override
    public Long createApp(AppAddDto appAddDto, User loginUser) {
        // 参数校验
        String initPrompt = appAddDto.getInitPrompt();
        ThrowUtils.throwIf(StrUtil.isBlank(initPrompt), ErrorCode.PARAMS_ERROR, "初始化 prompt 不能为空");
        // 构造入库对象
        App app = new App();
        BeanUtil.copyProperties(appAddDto, app);
        app.setUserId(loginUser.getId());
        // 应用名称暂时为 initPrompt 前 12 位
        app.setAppName(initPrompt.substring(0, Math.min(initPrompt.length(), 12)));
        // 创建链路不做 AI 路由，先写入空占位，首次生成时再回填真实类型
        app.setCodeGenType("");
        // 插入数据库
        boolean result = this.save(app);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        log.info("应用创建成功，ID: {}", app.getId());
        return app.getId();
    }

    @Override
    public AppVO getAppVO(App app) {
        if (app == null) {
            return null;
        }
        AppVO appVO = new AppVO();
        BeanUtil.copyProperties(app, appVO);
        // 关联创建用户信息
        Long userId = app.getUserId();
        if (userId != null) {
            User user = userService.getById(userId);
            appVO.setUser(userService.getUserVO(user));
        }
        return appVO;
    }

    @Override
    public List<AppVO> getAppVOList(List<App> appList) {
        if (CollUtil.isEmpty(appList)) {
            return new ArrayList<>();
        }
        // 批量查询用户，避免 N+1
        Set<Long> userIds = appList.stream()
                .map(App::getUserId)
                .filter(id -> id != null)
                .collect(Collectors.toSet());
        Map<Long, UserVO> userVOMap = userIds.isEmpty() ? Map.of()
                : userService.listByIds(userIds).stream()
                  .collect(Collectors.toMap(User::getId, user -> userService.getUserVO(user)));
        return appList.stream().map(app -> {
            AppVO appVO = new AppVO();
            BeanUtil.copyProperties(app, appVO);
            if (app.getUserId() != null) {
                appVO.setUser(userVOMap.get(app.getUserId()));
            }
            return appVO;
        }).collect(Collectors.toList());
    }

    @Override
    public QueryWrapper getQueryWrapper(AppQueryDto appQueryDto) {
        if (appQueryDto == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求参数为空");
        }
        Long id = appQueryDto.getId();
        String appName = appQueryDto.getAppName();
        String cover = appQueryDto.getCover();
        String initPrompt = appQueryDto.getInitPrompt();
        String codeGenType = appQueryDto.getCodeGenType();
        String deployKey = appQueryDto.getDeployKey();
        Integer priority = appQueryDto.getPriority();
        Long userId = appQueryDto.getUserId();
        String sortField = appQueryDto.getSortField();
        String sortOrder = appQueryDto.getSortOrder();
        return QueryWrapper.create()
                .eq("id", id)
                .like("appName", appName)
                .like("cover", cover)
                .like("initPrompt", initPrompt)
                .eq("codeGenType", codeGenType)
                .eq("deployKey", deployKey)
                .eq("priority", priority)
                .eq("userId", userId)
                .orderBy(sortField, "ascend".equals(sortOrder));
    }

    /**
     * 调用门面类,用ai生成代码
     *
     * @param appId
     * @param message
     * @param loginUser
     * @return
     */

    @Override
    public Flux<String> chatToGenCode(Long appId, String message, User loginUser) {
        // 1. 参数校验
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用 ID 不能为空");
        ThrowUtils.throwIf(StrUtil.isBlank(message), ErrorCode.PARAMS_ERROR, "用户消息不能为空");
        // 1.1 对用户原始输入做安全校验（敏感词、注入模式、长度上限）
        PromptSafetyInputGuardrail.validateRawUserPrompt(message);
        // 2. 查询应用信息
        App app = this.getById(appId);
        ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND_ERROR, "应用不存在");
        // 3. 验证用户是否有权限访问该应用，仅本人可以生成代码
        if (!app.getUserId().equals(loginUser.getId())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限访问该应用");
        }
        // 4. 获取应用的代码生成类型，首次生成时延迟路由并回填
        CodeGenTypeEnum codeGenTypeEnum = resolveCodeGenType(app, message);
        // 5. 通过校验后，添加用户消息到对话历史
        chatHistoryService.addChatMessage(appId, message, ChatHistoryMessageTypeEnum.USER.getValue(), loginUser.getId());
        // 6. 调用 AI 生成代码（流式）
        Flux<String> contentFlux = aiCodeGeneratorFacade.generateAndSaveCodeStream(message, codeGenTypeEnum, appId);
        // 7. 收集AI响应内容并在完成后记录到对话历史
        return streamHandlerExecutor.doExecute(contentFlux, chatHistoryService, appId, loginUser, codeGenTypeEnum);

    }

    /**
     * 获取代码生成类型；新建应用初始为空时，在首次生成前完成 AI 路由并持久化。
     */
    private CodeGenTypeEnum resolveCodeGenType(App app, String message) {
        String codeGenTypeStr = app.getCodeGenType();
        if (StrUtil.isNotBlank(codeGenTypeStr)) {
            CodeGenTypeEnum codeGenTypeEnum = CodeGenTypeEnum.getEnumByValue(codeGenTypeStr);
            if (codeGenTypeEnum == null) {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "不支持的代码生成类型");
            }
            return codeGenTypeEnum;
        }
        String routePrompt = StrUtil.blankToDefault(app.getInitPrompt(), message);
        CodeGenTypeEnum codeGenTypeEnum = codeGenTypeResolver.routeOrDefault(routePrompt);
        App updateApp = new App();
        updateApp.setId(app.getId());
        updateApp.setCodeGenType(codeGenTypeEnum.getValue());
        boolean updateResult = this.updateById(updateApp);
        ThrowUtils.throwIf(!updateResult, ErrorCode.OPERATION_ERROR, "更新应用代码生成类型失败");
        app.setCodeGenType(codeGenTypeEnum.getValue());
        log.info("应用首次生成时完成代码类型路由，ID: {}, 类型: {}", app.getId(), codeGenTypeEnum.getValue());
        return codeGenTypeEnum;
    }

    /**
     * 部署应用：将生成的代码从源目录复制到部署目录，并返回访问 URL
     *
     * @param appId
     * @param loginUser
     * @return
     */
    @Override
    public String deployApp(Long appId, User loginUser) {
        // 1. 参数校验
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用 ID 不能为空");
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NOT_LOGIN_ERROR, "用户未登录");
        // 2. 查询应用信息
        App app = this.getById(appId);
        ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND_ERROR, "应用不存在");
        // 3. 验证用户是否有权限部署该应用，仅本人可以部署
        if (!app.getUserId().equals(loginUser.getId())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限部署该应用");
        }
        // 4. 检查是否已有 deployKey
        String deployKey = app.getDeployKey();
        // 没有则生成 6 位 deployKey（大小写字母 + 数字）
        if (StrUtil.isBlank(deployKey)) {
            deployKey = RandomUtil.randomString(6);
        }
        // 5. 获取代码生成类型，构建源目录路径
        String codeGenType = app.getCodeGenType();
        CodeGenTypeEnum codeGenTypeEnum = CodeGenTypeEnum.getEnumByValue(codeGenType);
        ThrowUtils.throwIf(codeGenTypeEnum == null, ErrorCode.OPERATION_ERROR, "应用代码不存在，请先生成代码");
        String sourceDirName = codeGenTypeEnum.getValue() + "_" + appId;
        String sourceDirPath = AppConstant.CODE_OUTPUT_ROOT_DIR + File.separator + sourceDirName;
        // 6. 检查源目录是否存在
        File sourceDir = new File(sourceDirPath);
        if (!sourceDir.exists() || !sourceDir.isDirectory()) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "应用代码不存在，请先生成代码");
        }
        // 7. Vue 项目特殊处理：执行构建
        if (codeGenTypeEnum == CodeGenTypeEnum.VUE_PROJECT) {
            // Vue 项目需要构建
            boolean buildSuccess = vueProjectBuilder.buildProject(sourceDirPath);
            ThrowUtils.throwIf(!buildSuccess, ErrorCode.SYSTEM_ERROR, "Vue 项目构建失败，请检查代码和依赖");
            // 检查 dist 目录是否存在
            File distDir = new File(sourceDirPath, "dist");
            ThrowUtils.throwIf(!distDir.exists(), ErrorCode.SYSTEM_ERROR, "Vue 项目构建完成但未生成 dist 目录");
            // 将 dist 目录作为部署源
            sourceDir = distDir;
            log.info("Vue 项目构建成功，将部署 dist 目录: {}", distDir.getAbsolutePath());
        }


        // 8. 复制文件到部署目录
        String deployDirPath = AppConstant.CODE_DEPLOY_ROOT_DIR + File.separator + deployKey;
        try {
            FileUtil.copyContent(sourceDir, new File(deployDirPath), true);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "部署失败：" + e.getMessage());
        }
        // 9. 更新应用的 deployKey 和部署时间
        App updateApp = new App();
        updateApp.setId(appId);
        updateApp.setDeployKey(deployKey);
        updateApp.setDeployedTime(LocalDateTime.now());
        boolean updateResult = this.updateById(updateApp);
        ThrowUtils.throwIf(!updateResult, ErrorCode.OPERATION_ERROR, "更新应用部署信息失败");
        // 10. 构建应用访问 URL
        String appDeployUrl = String.format("%s/%s/", AppConstant.CODE_DEPLOY_HOST, deployKey);
        // 11. 异步生成截图并更新应用封面
        generateAppScreenshotAsync(appId, appDeployUrl);
        return appDeployUrl;

    }

    @Override
    public Long startNewConversation(Long appId, User loginUser) {
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用 ID 不能为空");
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NOT_LOGIN_ERROR, "用户未登录");
        App app = this.getById(appId);
        ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND_ERROR, "应用不存在");
        if (!app.getUserId().equals(loginUser.getId())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限操作该应用");
        }
        // 雪花 ID 与 KeyGenerators.snowFlakeId 同源,不会与现有 chat_history.sessionId 冲突
        long newSessionId = IdUtil.getSnowflakeNextId();
        App updateApp = new App();
        updateApp.setId(appId);
        updateApp.setCurrentSessionId(newSessionId);
        boolean updated = this.updateById(updateApp);
        ThrowUtils.throwIf(!updated, ErrorCode.OPERATION_ERROR, "刷新对话会话失败");
        // 必须清缓存:旧 AiServices 实例持有的 ChatMemory 已经把旧 session 历史加载进了内存,
        // 不清就会继续用脏 memory 跑新对话
        aiCodeGeneratorServiceFactory.evictByAppId(appId);
        log.info("应用开启新对话, appId={}, newSessionId={}", appId, newSessionId);
        return newSessionId;
    }

    /**
     * 删除应用时关联删除对话历史
     *
     * @param id 应用ID
     * @return 是否成功
     */
    @Override
    public boolean removeById(Serializable id) {
        if (id == null) {
            return false;
        }
        // 转换为 Long 类型
        Long appId = Long.valueOf(id.toString());
        if (appId <= 0) {
            return false;
        }
        // 先删除关联的对话历史
        try {
            chatHistoryService.deleteByAppId(appId);
        } catch (Exception e) {
            // 记录日志但不阻止应用删除
            log.error("删除应用关联对话历史失败: {}", e.getMessage());
        }
        // 删除应用
        return super.removeById(id);
    }




    /**
     * 异步生成应用截图并更新封面
     *
     * @param appId  应用ID
     * @param appUrl 应用访问URL
     */
    @Override
    public void generateAppScreenshotAsync(Long appId, String appUrl) {
        // 使用虚拟线程异步执行
        Thread.startVirtualThread(() -> {
            // 调用截图服务生成截图并上传
            String screenshotUrl = screenshotService.generateAndUploadScreenshot(appUrl);
            // 更新应用封面字段
            App updateApp = new App();
            updateApp.setId(appId);
            updateApp.setCover(screenshotUrl);
            boolean updated = this.updateById(updateApp);
            ThrowUtils.throwIf(!updated, ErrorCode.OPERATION_ERROR, "更新应用封面字段失败");
        });
    }

}

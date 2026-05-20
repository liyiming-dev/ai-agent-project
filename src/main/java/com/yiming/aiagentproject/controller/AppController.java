package com.yiming.aiagentproject.controller;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import com.yiming.aiagentproject.ai.model.enums.CodeGenTypeEnum;
import com.yiming.aiagentproject.annotation.AuthCheck;
import com.yiming.aiagentproject.common.BaseResponse;
import com.yiming.aiagentproject.common.DeleteRequest;
import com.yiming.aiagentproject.common.ResultUtils;
import com.yiming.aiagentproject.constant.AppConstant;
import com.yiming.aiagentproject.constant.UserConstant;
import com.yiming.aiagentproject.dto.app.*;
import com.yiming.aiagentproject.exception.BusinessException;
import com.yiming.aiagentproject.exception.ErrorCode;
import com.yiming.aiagentproject.exception.ThrowUtils;
import com.yiming.aiagentproject.model.entity.App;
import com.yiming.aiagentproject.model.entity.User;
import com.yiming.aiagentproject.rateLimiter.annotation.RateLimit;
import com.yiming.aiagentproject.rateLimiter.enums.RateLimitType;
import com.yiming.aiagentproject.service.AppService;
import com.yiming.aiagentproject.service.ProjectDownloadService;
import com.yiming.aiagentproject.service.UserService;
import com.yiming.aiagentproject.vo.AppVO;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.File;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 应用 控制层。
 *
 *
 * @author yiming
 */
@RestController
@RequestMapping("/app")
public class AppController {

    @Autowired
    private AppService appService;

    @Autowired
    private UserService userService;
    @Resource
    private ProjectDownloadService projectDownloadService;


    // region 用户功能

    /**
     * 下载应用代码
     *
     * @param appId    应用ID
     * @param request  请求
     * @param response 响应
     */
    @GetMapping("/download/{appId}")
    public void downloadAppCode(@PathVariable Long appId,
                                HttpServletRequest request,
                                HttpServletResponse response) {
        // 1. 基础校验
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用ID无效");
        // 2. 查询应用信息
        App app = appService.getById(appId);
        ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND_ERROR, "应用不存在");
        // 3. 权限校验：只有应用创建者可以下载代码
        User loginUser = userService.getLoginUser(request);
        if (!app.getUserId().equals(loginUser.getId())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限下载该应用代码");
        }
        // 4. 构建应用代码目录路径（生成目录，非部署目录）
        CodeGenTypeEnum codeGenType = CodeGenTypeEnum.getEnumByValue(app.getCodeGenType());
        ThrowUtils.throwIf(codeGenType == null, ErrorCode.NOT_FOUND_ERROR, "应用代码不存在，请先生成代码");
        String sourceDirName = codeGenType.getValue() + "_" + appId;
        String sourceDirPath = AppConstant.CODE_OUTPUT_ROOT_DIR + File.separator + sourceDirName;
        // 5. 检查代码目录是否存在
        File sourceDir = new File(sourceDirPath);
        ThrowUtils.throwIf(!sourceDir.exists() || !sourceDir.isDirectory(),
                ErrorCode.NOT_FOUND_ERROR, "应用代码不存在，请先生成代码");
        // 6. 生成下载文件名（不建议添加中文内容）
        String downloadFileName = String.valueOf(appId);
        // 7. 调用通用下载服务
        projectDownloadService.downloadProjectAsZip(sourceDirPath, downloadFileName, response);
    }


    @PostMapping("/add")
    public BaseResponse<Long> addApp(@RequestBody AppAddDto appAddDto, HttpServletRequest request) {
        ThrowUtils.throwIf(appAddDto == null, ErrorCode.PARAMS_ERROR);
        // 获取当前登录用户
        User loginUser = userService.getLoginUser(request);
        Long appId = appService.createApp(appAddDto, loginUser);
        return ResultUtils.success(appId);
    }

    /**
     * 用户根据 id 修改自己的应用（仅支持修改名称）
     */
    @PostMapping("/update")
    public BaseResponse<Boolean> updateApp(@RequestBody AppUpdateDto appUpdateDto, HttpServletRequest request) {
        ThrowUtils.throwIf(appUpdateDto == null || appUpdateDto.getId() == null || appUpdateDto.getId() <= 0,
                ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        App oldApp = appService.getById(appUpdateDto.getId());
        ThrowUtils.throwIf(oldApp == null, ErrorCode.NOT_FOUND_ERROR);
        ThrowUtils.throwIf(!oldApp.getUserId().equals(loginUser.getId()), ErrorCode.NO_AUTH_ERROR);
        App app = new App();
        app.setId(appUpdateDto.getId());
        app.setAppName(appUpdateDto.getAppName());
        app.setEditTime(LocalDateTime.now());
        boolean result = appService.updateById(app);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(true);
    }

    /**
     * 用户根据 id 删除自己的应用
     */
    @PostMapping("/delete")
    public BaseResponse<Boolean> deleteApp(@RequestBody DeleteRequest deleteRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(deleteRequest == null || deleteRequest.getId() <= 0, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        App oldApp = appService.getById(deleteRequest.getId());
        ThrowUtils.throwIf(oldApp == null, ErrorCode.NOT_FOUND_ERROR);
        // 仅创建者或管理员可删除
        if (!oldApp.getUserId().equals(loginUser.getId())
                && !UserConstant.ADMIN_ROLE.equals(loginUser.getUserRole())) {
            ThrowUtils.throwIf(true, ErrorCode.NO_AUTH_ERROR);
        }
        boolean result = appService.removeById(deleteRequest.getId());
        return ResultUtils.success(result);
    }

    /**
     * 根据 id 查看应用详情
     */
    @GetMapping("/get/vo")
    public BaseResponse<AppVO> getAppVOById(long id) {
        ThrowUtils.throwIf(id <= 0, ErrorCode.PARAMS_ERROR);
        App app = appService.getById(id);
        ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND_ERROR);
        return ResultUtils.success(appService.getAppVO(app));
    }

    /**
     * 分页查询自己的应用列表（支持按名称查询，每页最多 20 个）
     */
    @PostMapping("/my/list/page/vo")
    public BaseResponse<Page<AppVO>> listMyAppVOByPage(@RequestBody AppQueryDto appQueryDto, HttpServletRequest request) {
        ThrowUtils.throwIf(appQueryDto == null, ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(appQueryDto.getPageSize() > 20, ErrorCode.PARAMS_ERROR, "每页最多 20 个");
        User loginUser = userService.getLoginUser(request);
        // 仅查询自己的，只允许按名称过滤，按创建时间倒序
        AppQueryDto safeQuery = new AppQueryDto();
        safeQuery.setAppName(appQueryDto.getAppName());
        safeQuery.setUserId(loginUser.getId());
        safeQuery.setPageNum(appQueryDto.getPageNum());
        safeQuery.setPageSize(appQueryDto.getPageSize());
        safeQuery.setSortField("createTime");
        safeQuery.setSortOrder("descend");
        long pageNum = safeQuery.getPageNum();
        long pageSize = safeQuery.getPageSize();
        Page<App> appPage = appService.page(Page.of(pageNum, pageSize), appService.getQueryWrapper(safeQuery));
        Page<AppVO> appVOPage = new Page<>(pageNum, pageSize, appPage.getTotalRow());
        appVOPage.setRecords(appService.getAppVOList(appPage.getRecords()));
        return ResultUtils.success(appVOPage);
    }

    /**
     * 分页查询精选应用列表（按优先级，支持按名称查询，每页最多 20 个）
     */
    @PostMapping("/good/list/page/vo")
    @Cacheable(
            value = "good_app_page",
            key = "T(com.yiming.aiagentproject.utils.CacheKeyUtils).generateKey(#appQueryDto)",
            condition = "#appQueryDto.pageNum <= 10"
    )
    public BaseResponse<Page<AppVO>> listGoodAppVOByPage(@RequestBody AppQueryDto appQueryDto) {
        ThrowUtils.throwIf(appQueryDto == null, ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(appQueryDto.getPageSize() > 20, ErrorCode.PARAMS_ERROR, "每页最多 20 个");
        long pageNum = appQueryDto.getPageNum();
        long pageSize = appQueryDto.getPageSize();
        // 精选：优先级 >= 99，按创建时间倒序
        QueryWrapper queryWrapper = QueryWrapper.create()
                .like("appName", appQueryDto.getAppName())
                .ge("priority", AppConstant.GOOD_APP_PRIORITY)
                .orderBy("createTime", false);
        Page<App> appPage = appService.page(Page.of(pageNum, pageSize), queryWrapper);
        Page<AppVO> appVOPage = new Page<>(pageNum, pageSize, appPage.getTotalRow());
        appVOPage.setRecords(appService.getAppVOList(appPage.getRecords()));
        return ResultUtils.success(appVOPage);
    }

    //

    // region 管理员功能

    /**
     * 管理员根据 id 删除任意应用
     */
    @PostMapping("/admin/delete")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> deleteAppByAdmin(@RequestBody DeleteRequest deleteRequest) {
        ThrowUtils.throwIf(deleteRequest == null || deleteRequest.getId() <= 0, ErrorCode.PARAMS_ERROR);
        boolean result = appService.removeById(deleteRequest.getId());
        return ResultUtils.success(result);
    }

    /**
     * 管理员根据 id 更新任意应用（appName、cover、priority）
     */
    @PostMapping("/admin/update")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> updateAppByAdmin(@RequestBody AppAdminUpdateDto appAdminUpdateDto) {
        ThrowUtils.throwIf(appAdminUpdateDto == null || appAdminUpdateDto.getId() == null
                || appAdminUpdateDto.getId() <= 0, ErrorCode.PARAMS_ERROR);
        App oldApp = appService.getById(appAdminUpdateDto.getId());
        ThrowUtils.throwIf(oldApp == null, ErrorCode.NOT_FOUND_ERROR);
        App app = new App();
        BeanUtil.copyProperties(appAdminUpdateDto, app);
        app.setEditTime(LocalDateTime.now());
        boolean result = appService.updateById(app);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(true);
    }

    /**
     * 管理员分页查询应用列表（支持按除时间外的任意字段查询，每页数量不限）
     */
    @PostMapping("/admin/list/page/vo")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Page<AppVO>> listAppVOByPageByAdmin(@RequestBody AppQueryDto appQueryDto) {
        ThrowUtils.throwIf(appQueryDto == null, ErrorCode.PARAMS_ERROR);
        long pageNum = appQueryDto.getPageNum();
        long pageSize = appQueryDto.getPageSize();
        appQueryDto.setSortField("createTime");
        appQueryDto.setSortOrder("descend");
        Page<App> appPage = appService.page(Page.of(pageNum, pageSize),
                appService.getQueryWrapper(appQueryDto));
        Page<AppVO> appVOPage = new Page<>(pageNum, pageSize, appPage.getTotalRow());
        appVOPage.setRecords(appService.getAppVOList(appPage.getRecords()));
        return ResultUtils.success(appVOPage);
    }

    /**
     * 管理员根据 id 查看应用详情
     */
    @GetMapping("/admin/get/vo")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<AppVO> getAppVOByIdByAdmin(long id) {
        ThrowUtils.throwIf(id <= 0, ErrorCode.PARAMS_ERROR);
        App app = appService.getById(id);
        ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND_ERROR);
        return ResultUtils.success(appService.getAppVO(app));
    }

    /**
     * 应用聊天生成代码（流式 SSE）
     *
     * @param appId   应用 ID
     * @param message 用户消息
     * @param request 请求对象
     * @return 生成结果流
     */
    @GetMapping(value = "/chat/gen/code", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @RateLimit(limitType = RateLimitType.USER, rate = 5, rateInterval = 60, message = "AI请求过于频繁,请稍后再试")
    public Flux<ServerSentEvent<String>> chatToGenCode(@RequestParam Long appId,
                                                       @RequestParam String message,
                                                       HttpServletRequest request) {
        // 参数校验
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用ID无效");
        ThrowUtils.throwIf(StrUtil.isBlank(message), ErrorCode.PARAMS_ERROR, "用户消息不能为空");
        // 获取当前登录用户
        User loginUser = userService.getLoginUser(request);
        // 调用服务生成代码（流式）
        Flux<String> stringFlux = appService.chatToGenCode(appId, message, loginUser);
        return stringFlux.map(chunk -> {
            Map<String, String> wrapper = Map.of("d", chunk);
            String jsonData = JSONUtil.toJsonStr(wrapper);
            return ServerSentEvent.<String>builder()
                    .data(jsonData)
                    .build();
        }).concatWith(Mono.just(
                //发送结束事件
                ServerSentEvent.<String>builder()
                        .event("done")
                        .data("")
                        .build()
        ));
    }

    /**
     * 开启新对话:刷新 app.currentSessionId 并清缓存,下一次 chatToGenCode 重新装载 ChatMemory
     * 解决"换轨幻觉":同 appId 历史(可能是完全不同主题)被装入 prompt 导致 reasoning 模型在长生成中漂移
     */
    @PostMapping("/new-conversation")
    public BaseResponse<Long> startNewConversation(@RequestBody AppNewConversationDto dto, HttpServletRequest request) {
        ThrowUtils.throwIf(dto == null, ErrorCode.PARAMS_ERROR);
        Long appId = dto.getAppId();
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用 ID 不能为空");
        User loginUser = userService.getLoginUser(request);
        Long newSessionId = appService.startNewConversation(appId, loginUser);
        return ResultUtils.success(newSessionId);
    }

    /**
     * 应用部署
     *
     * @param appDeployDto 部署请求
     * @param request          请求
     * @return 部署 URL
     */
    @PostMapping("/deploy")
    public BaseResponse<String> deployApp(@RequestBody AppDeployDto appDeployDto, HttpServletRequest request) {
        ThrowUtils.throwIf(appDeployDto == null, ErrorCode.PARAMS_ERROR);
        Long appId = appDeployDto.getAppId();
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用 ID 不能为空");
        // 获取当前登录用户
        User loginUser = userService.getLoginUser(request);
        // 调用服务部署应用
        String deployUrl = appService.deployApp(appId, loginUser);
        return ResultUtils.success(deployUrl);
    }



    // endregion
}

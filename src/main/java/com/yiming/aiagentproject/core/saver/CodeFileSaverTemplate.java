package com.yiming.aiagentproject.core.saver;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.yiming.aiagentproject.ai.model.enums.CodeGenTypeEnum;
import com.yiming.aiagentproject.exception.BusinessException;
import com.yiming.aiagentproject.exception.ErrorCode;

import java.io.File;
import java.nio.charset.StandardCharsets;


/**
 * 抽象文件保存器 - 模板方法模式
 * @param <T>
 */
public abstract class CodeFileSaverTemplate<T> {
    // 文件保存根目录
    private static final String FILE_SAVE_ROOT_DIR = System.getProperty("user.dir") + "/tmp/code_output";

    /**
     * 模板方法 - 保存代码的标准流程
     * @param result
     * @return
     */
    public final File saveCode(T result){
        //1. 验证输入
        validateInput(result);
        //2. 创建文件目录
        String baseDirPath = buildUniqueDir();
        //3. 保存文件(让子类去实现)
        saveFiles(result, baseDirPath);
        //4. 返回文件目录对象
        return new File(baseDirPath);
    }



    /**
     * 构建唯一目录路径：tmp/code_output/bizType_雪花ID
     */
    protected final String buildUniqueDir() {
        String codeType = getCodeType().getValue();
        String uniqueDirName = StrUtil.format("{}_{}", codeType, IdUtil.getSnowflakeNextIdStr());
        String dirPath = FILE_SAVE_ROOT_DIR + File.separator + uniqueDirName;
        FileUtil.mkdir(dirPath);
        return dirPath;
    }

    /**
     * 验证输入方法 - 由模板类统一管理
     * @param result
     */
    protected void validateInput(T result) {
        if (result == null){
            throw new BusinessException(ErrorCode.SYSTEM_ERROR,"代码结果不能为空");
        }

    }
    /**
     * 写入单个文件
     */
    public static void writeToFile(String dirPath, String filename, String content) {
        if (StrUtil.isNotBlank(content)) {
            String filePath = dirPath + File.separator + filename;
            FileUtil.writeString(content, filePath, StandardCharsets.UTF_8);
        }
    }

    /**
     * 保存文件方法
     * @param result
     * @param baseDirPath
     * @return
     */
    protected abstract void saveFiles(T result, String baseDirPath);
    protected abstract CodeGenTypeEnum getCodeType();
}

package com.yiming.aiagentproject.core.saver;

import com.yiming.aiagentproject.ai.model.MultiFileCodeResult;
import com.yiming.aiagentproject.ai.model.enums.CodeGenTypeEnum;

public class MultiFileCodeFileSaverTemplate extends CodeFileSaverTemplate<MultiFileCodeResult> {
    @Override
    protected void saveFiles(MultiFileCodeResult result, String baseDirPath) {
        //保存HTML文件
        writeToFile(baseDirPath, "index.html", result.getHtmlCode());

        //保存CSS文件
        writeToFile(baseDirPath, "style.css", result.getCssCode());

        //保存JS文件
        writeToFile(baseDirPath, "script.js", result.getJsCode());
    }

    @Override
    protected CodeGenTypeEnum getCodeType() {
        return CodeGenTypeEnum.MULTI_FILE;
    }
}

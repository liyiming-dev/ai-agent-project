package com.yiming.aiagentproject.core.saver;

import com.yiming.aiagentproject.ai.model.MultiFileCodeResult;
import com.yiming.aiagentproject.ai.model.enums.CodeGenTypeEnum;

public class MultiFileCodeFileSaverTemplate extends CodeFileSaverTemplate<MultiFileCodeResult> {
    @Override
    protected void saveFiles(MultiFileCodeResult result, String baseDirPath) {
        //保存HTML文件
        String htmlFilePath = baseDirPath + "/index.html";
        writeToFile(htmlFilePath, "index.html", result.getHtmlCode());

        //保存CSS文件
        String cssFilePath = baseDirPath + "/style.css";
        writeToFile(cssFilePath, "style.css", result.getCssCode());

        //保存JS文件
        String jsFilePath = baseDirPath + "/script.js";
        writeToFile(jsFilePath, "script.js", result.getJsCode());
    }

    @Override
    protected CodeGenTypeEnum getCodeType() {
        return CodeGenTypeEnum.MULTI_FILE;
    }
}

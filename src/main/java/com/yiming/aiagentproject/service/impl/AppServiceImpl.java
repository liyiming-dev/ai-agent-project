package com.yiming.aiagentproject.service.impl;

import com.mybatisflex.spring.service.impl.ServiceImpl;
import com.yiming.aiagentproject.model.entity.App;
import com.yiming.aiagentproject.mapper.AppMapper;
import com.yiming.aiagentproject.service.AppService;
import org.springframework.stereotype.Service;

/**
 * 应用 服务层实现。
 *
 * @author <a href="https://github.com/liyupi">程序员鱼皮</a>
 */
@Service
public class AppServiceImpl extends ServiceImpl<AppMapper, App>  implements AppService{

}

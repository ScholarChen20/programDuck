package com.yupi.mianshiya.blackfilter;

import com.alibaba.nacos.api.annotation.NacosInjected;
import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.config.listener.Listener;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.validation.constraints.NotNull;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Nacos 监听器
 * 通过Nacos配置中心监听配置变化，当配置变化时，调用BlackIpUtils.rebuildBlackIp方法重新构建布隆过滤器中的黑名单。
 * Nacos配置变化后，会立即触发监听器的receiveConfigInfo方法，该方法会调用重建黑名单的方法，因此可以保证及时生效（取决于网络和重建速度）。
 * @author <a href="https://github.com/liyupi">程序员鱼皮</a>
 * @from <a href="https://www.code-nav.cn">编程导航学习圈</a>
 */
@Slf4j
// todo 取消注释开启 Nacos（须先配置 Nacos）
//@Component
public class NacosListener implements InitializingBean {

    @NacosInjected
    private ConfigService configService;

    @Value("${nacos.config.data-id}")
    private String dataId;

    @Value("${nacos.config.group}")
    private String group;

    @Override
    public void afterPropertiesSet() throws Exception {
        log.info("nacos 监听器启动");

        /**
         * 监听 Nacos 配置，并异步刷新黑名单
         */
        String config = configService.getConfigAndSignListener(dataId, group, 3000L, new Listener() { // 监听器，监听配置信息变化
            final ThreadFactory threadFactory = new ThreadFactory() { // 创建线程工厂，用于创建线程
                private final AtomicInteger poolNumber = new AtomicInteger(1); // 线程池编号

                @Override
                public Thread newThread(@NotNull Runnable r) {
                    Thread thread = new Thread(r);
                    thread.setName("refresh-ThreadPool" + poolNumber.getAndIncrement());
                    return thread;
                }
            };
            final ExecutorService executorService = Executors.newFixedThreadPool(1, threadFactory);  // 创建线程池，用于异步处理刷新黑名单的逻辑

            // 通过线程池异步处理黑名单变化的逻辑
            @Override
            public Executor getExecutor() {
                return executorService;
            }

            // 监听后续黑名单变化，刷新黑名单，线程池会处理刷新逻辑
            @Override
            public void receiveConfigInfo(String configInfo) {
                log.info("监听到配置信息变化：{}", configInfo);
                BlackIpUtils.rebuildBlackIp(configInfo);
            }
        });

        // 初始化黑名单
        BlackIpUtils.rebuildBlackIp(config);
    }
}
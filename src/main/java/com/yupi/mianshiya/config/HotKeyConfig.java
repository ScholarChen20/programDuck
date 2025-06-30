package com.yupi.mianshiya.config;

import com.jd.platform.hotkey.client.ClientStarter;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * hotkey 热 key 发现配置
 */
// todo 取消注释开启 HotKey（须先配置 HotKey）,默认是注销的
//@Configuration
//@ConfigurationProperties(prefix = "hotkey")
@Data
public class HotKeyConfig {

    /**
     * Etcd 服务器完整地址
     */
    private String etcdServer = "http://127.0.0.1:2379";

    /**
     * 应用名称
     */
    private String appName = "app";

    /**
     * 本地缓存最大数量
     */
    private int caffeineSize = 10000;

    /**
     * 批量推送 key 的间隔时间
     */
    private long pushPeriod = 1000L;

    /**
     * 初始化 hotkey
     */
    @Bean
    public void initHotkey() {
        ClientStarter.Builder builder = new ClientStarter.Builder(); // 创建热key启动器
        ClientStarter starter = builder.setAppName(appName) // 设置应用名称
                .setCaffeineSize(caffeineSize) // 设置本地缓存最大数量
                .setPushPeriod(pushPeriod) // 设置批量推送 key 的间隔时间
                .setEtcdServer(etcdServer) // 设置 Etcd 服务器完整地址
                .build(); // 启动热key
        starter.startPipeline(); // 启动热key流水线
    }

}

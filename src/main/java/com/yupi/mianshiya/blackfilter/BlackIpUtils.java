package com.yupi.mianshiya.blackfilter;

import cn.hutool.bloomfilter.BitMapBloomFilter;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;
import org.yaml.snakeyaml.Yaml;

import java.util.List;
import java.util.Map;

/**
 * 黑名单过滤工具类
 *
 * @author <a href="https://github.com/liyupi">程序员鱼皮</a>
 * @from <a href="https://www.code-nav.cn">编程导航学习圈</a>
 */
@Slf4j
public class BlackIpUtils {

    private static BitMapBloomFilter bloomFilter = new BitMapBloomFilter(100); // 初始化布隆过滤器, 初始大小为 100

    // 判断 ip 是否在黑名单里
    public static boolean isBlackIp(String ip) {
        return bloomFilter.contains(ip);
    }

    /**
     * 重建 ip 黑名单
     * 具体实现过程：
     * 1. 解析 yaml 文件，获取 IP 黑名单列表
     * 2. 构建新的布隆过滤器，并将 IP 黑名单列表中的 IP 加入到新的布隆过滤器中
     * 3. 将新的布隆过滤器赋值给 bloomFilter 变量
     * 4. 释放锁
     * @param configInfo
     */
    public static void rebuildBlackIp(String configInfo) {
        if (StrUtil.isBlank(configInfo)) {
            configInfo = "{}";
        }
        // 解析 yaml 文件,yaml文件从哪里来？ 前端传过来，怎么解析？
        Yaml yaml = new Yaml();
        Map map = yaml.loadAs(configInfo, Map.class); // yaml文件从哪里来？ 前端传过来，怎么解析？
        // 获取 IP 黑名单
        List<String> blackIpList = (List<String>) map.get("blackIpList");
        // 加锁防止并发
        synchronized (BlackIpUtils.class) {
            if (CollUtil.isNotEmpty(blackIpList)) { // 如果不为空，则重新构建
                // 注意构造参数的设置
                BitMapBloomFilter bitMapBloomFilter = new BitMapBloomFilter(958506);
                for (String blackIp : blackIpList) {
                    bitMapBloomFilter.add(blackIp); // 将 IP 黑名单列表中的 IP 加入到新的布隆过滤器中
                }
                bloomFilter = bitMapBloomFilter;
            } else { // 如果为空，则初始化一个空的布隆过滤器
                bloomFilter = new BitMapBloomFilter(100);
            }
        }
    }
}

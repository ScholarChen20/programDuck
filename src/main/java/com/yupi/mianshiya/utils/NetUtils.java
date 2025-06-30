package com.yupi.mianshiya.utils;

import java.net.InetAddress;
import javax.servlet.http.HttpServletRequest;

/**
 * 网络工具类
 *
 * @author <a href="https://github.com/liyupi">程序员鱼皮</a>
 * @from <a href="https://yupi.icu">编程导航知识星球</a>
 */
public class NetUtils {

    /**
     * 获取客户端 IP 地址
     * 实现过程：
     * 1. 获取代理，兼容 ngin，获取第一个非 unknown 的值
     * 1. 获取请求头中的 X-Forwarded-For，如果存在，则取第一个非 unknown 的 IP 地址作为客户端 IP 地址；
     * 2. 如果不存在，则获取请求头中的 X-Real-IP，如果存在，则取该值作为客户端 IP 地址；
     * 3. 如果不存在，则获取请求头中的 Remote-Addr，如果存在，则取该值作为客户端 IP 地址；
     * 4. 如果以上三个值都不存在，则取本机 IP 地址作为客户端 IP 地址。
     * @param request
     * @return
     */
    public static String getIpAddress(HttpServletRequest request) {
        String ip = request.getHeader("x-forwarded-for"); // 获取代理，兼容 ngin，获取第一个非 unknown 的值
        if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
            if (ip.equals("127.0.0.1")) {
                // 根据网卡取本机配置的 IP
                InetAddress inet = null;
                try {
                    inet = InetAddress.getLocalHost();
                } catch (Exception e) {
                    e.printStackTrace();
                }
                if (inet != null) {
                    ip = inet.getHostAddress();
                }
            }
        }
        // 多个代理的情况，第一个IP为客户端真实IP,多个IP按照','分割
        if (ip != null && ip.length() > 15) {
            if (ip.indexOf(",") > 0) {
                ip = ip.substring(0, ip.indexOf(","));
            }
        }
        if (ip == null) {
            return "127.0.0.1";
        }
        return ip;
    }

}

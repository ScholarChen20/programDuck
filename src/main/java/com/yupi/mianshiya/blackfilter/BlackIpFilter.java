package com.yupi.mianshiya.blackfilter;

import com.yupi.mianshiya.utils.NetUtils;

import javax.servlet.*;
import javax.servlet.annotation.WebFilter;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;

/**
 * 全局 IP 黑名单过滤请求拦截器
 *
 * @author <a href="https://github.com/liyupi">程序员鱼皮</a>
 * @from <a href="https://www.code-nav.cn">编程导航学习圈</a>
 */
@WebFilter(urlPatterns = "/*", filterName = "blackIpFilter")
public class BlackIpFilter implements Filter {

    /**
     * 黑名单 IP 工具类
     * 添加 IP 到黑名单，则该 IP 访问所有接口都返回错误信息，禁止访问
     * 删除 IP，则该 IP 解除黑名单限制
     * 获取所有 IP 黑名单列表，返回 JSON 格式数据
     * @param servletRequest
     * @param servletResponse
     * @param filterChain
     * @throws IOException
     * @throws ServletException
     */
    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {

        String ipAddress = NetUtils.getIpAddress((HttpServletRequest) servletRequest); // 获取 IP 地址
        if (BlackIpUtils.isBlackIp(ipAddress)) { // 判断是否为黑名单 IP
            servletResponse.setContentType("text/json;charset=UTF-8"); // 设置返回的 Content-Type
            servletResponse.getWriter().write("{\"errorCode\":\"-1\",\"errorMsg\":\"黑名单IP，禁止访问\"}"); // 返回错误信息，禁止访问
            return;
        }
        filterChain.doFilter(servletRequest, servletResponse); // 放行
    }

}
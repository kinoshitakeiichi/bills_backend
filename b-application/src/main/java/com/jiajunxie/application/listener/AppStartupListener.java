package com.jiajunxie.application.listener;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.net.InetAddress;
import java.net.UnknownHostException;

@Component
public class AppStartupListener implements ApplicationListener<ApplicationReadyEvent> {

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        Environment env = event.getApplicationContext().getEnvironment();
        String port = env.getProperty("server.port", "8080");
        String contextPath = env.getProperty("server.servlet.context-path", "");
        
        // 自动获取容器或宿主机的 IP 地址
        String ip = "daikichi.local";
        try {
            ip = InetAddress.getLocalHost().getHostAddress();
        } catch (UnknownHostException e) {
            System.out.println("无法获取外部 IP 地址，默认使用 daikichi.local");
        }

        // 获取 Java 25 运行时环境细节
        RuntimeMXBean runtimeMXBean = ManagementFactory.getRuntimeMXBean();
        String javaVersion = System.getProperty("java.version");
        String vmName = System.getProperty("java.vm.name");
        
        // 检查虚拟线程是否在 Spring 环境中成功开启
        boolean isVirtualEnabled = Boolean.parseBoolean(env.getProperty("spring.threads.virtual.enabled", "false"));

        // 🌟 惊艳的控制台全量环境与 URL 打印模板
        System.out.println("""
            
            ===================================================================================
            🚀  Bill Backend 服务启动成功！架构与前沿环境对齐信息如下：
            ===================================================================================
            💻  [运行时环境]
                - Java 核心版本 : Java %s (基于 %s)
                - 虚拟机运行时间 : %d 秒
                - 🚀 虚拟线程状态 : %s
            
            🌐  [本地与容器访问 URL]
                - 本地环境入口   : http://daikichi.local:%s%s
                - 容器集群内网络 : http://%s:%s%s
            
            📝  [OpenAPI / Swagger 接口文档契约]
                - 精美文档界面   : http://daikichi.local:%s%s/swagger-ui/index.html
                - 原始 JSON 定义 : http://daikichi.local:%s%s/v3/api-docs
            ===================================================================================
            """.formatted(
                javaVersion, vmName, runtimeMXBean.getUptime() / 1000,
                isVirtualEnabled ? "🟢 已完美激活 (Project Loom 虚拟线程)" : "🔴 未开启",
                port, contextPath,
                ip, port, contextPath,
                port, contextPath,
                port, contextPath
        ));
  } 
}


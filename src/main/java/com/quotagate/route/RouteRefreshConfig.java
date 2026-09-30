package com.quotagate.route;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
public class RouteRefreshConfig {
    //使用受 Spring 管理的专用线程池，避免把数据库刷新任务丢到 JVM 公共线程池。

    @Bean("routeRefreshExecutor")
    Executor routeRefreshExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
//        队列设为 100，是因为刷新任务按模型别名合并，正常情况下不会大量堆积。
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("route-refresh-");
        executor.initialize();
        return executor;
    }
}
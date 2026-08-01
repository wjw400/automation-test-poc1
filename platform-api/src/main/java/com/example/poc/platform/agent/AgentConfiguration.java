package com.example.poc.platform.agent;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(AgentProperties.class)
public class AgentConfiguration {
    @Bean(name = "agentRuntimeExecutor", destroyMethod = "shutdown")
    ExecutorService agentRuntimeExecutor() {
        return Executors.newFixedThreadPool(4, runnable -> {
            Thread thread = new Thread(runnable, "agent-runtime");
            thread.setDaemon(true);
            return thread;
        });
    }

    @Bean(name = "agentScheduler", destroyMethod = "shutdown")
    ScheduledExecutorService agentScheduler() {
        return Executors.newScheduledThreadPool(2, runnable -> {
            Thread thread = new Thread(runnable, "agent-scheduler");
            thread.setDaemon(true);
            return thread;
        });
    }

    @Bean
    AgentRuntimeResources agentRuntimeResources(
            @Qualifier("agentRuntimeExecutor") ExecutorService executor,
            @Qualifier("agentScheduler") ScheduledExecutorService scheduler) {
        return new AgentRuntimeResources(executor, scheduler);
    }
}

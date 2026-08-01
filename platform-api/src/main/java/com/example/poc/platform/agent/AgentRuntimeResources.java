package com.example.poc.platform.agent;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;

public record AgentRuntimeResources(ExecutorService executor, ScheduledExecutorService scheduler) {}

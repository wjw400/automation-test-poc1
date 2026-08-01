package com.example.poc.platform.agent;

import com.example.poc.platform.agent.AgentTaskService.AgentEventRecord;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
public class AgentEventStreamService {
    private final AgentTaskService taskService;
    private final AgentRuntimeResources resources;
    private final Map<String, CopyOnWriteArrayList<Subscriber>> subscribers = new ConcurrentHashMap<>();

    public AgentEventStreamService(AgentTaskService taskService, AgentRuntimeResources resources) {
        this.taskService = taskService;
        this.resources = resources;
    }

    public SseEmitter subscribe(String taskId, String actorId, long afterSequence) {
        taskService.get(taskId, actorId);
        SseEmitter emitter = new SseEmitter(0L);
        Subscriber subscriber = new Subscriber(taskId, emitter, afterSequence);
        subscribers.computeIfAbsent(taskId, ignored -> new CopyOnWriteArrayList<>()).add(subscriber);
        Runnable cleanup = () -> remove(subscriber);
        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError(ignored -> cleanup.run());
        subscriber.replay(taskService.events(taskId, actorId, afterSequence));
        return emitter;
    }

    public void close(String taskId) {
        List<Subscriber> taskSubscribers = subscribers.remove(taskId);
        if (taskSubscribers == null) return;
        for (Subscriber subscriber : taskSubscribers) subscriber.close();
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onCommitted(AgentEventCommitted committed) {
        AgentEventRecord event = committed.event();
        List<Subscriber> taskSubscribers = subscribers.get(event.taskId());
        if (taskSubscribers == null) return;
        for (Subscriber subscriber : taskSubscribers) subscriber.publish(event);
        if (isTerminal(event.eventType())) close(event.taskId());
    }

    private boolean isTerminal(String eventType) {
        return "task.completed".equals(eventType)
                || "task.failed".equals(eventType)
                || "task.cancelled".equals(eventType);
    }

    private void remove(Subscriber subscriber) {
        CopyOnWriteArrayList<Subscriber> taskSubscribers = subscribers.get(subscriber.taskId);
        if (taskSubscribers == null) return;
        taskSubscribers.remove(subscriber);
        if (taskSubscribers.isEmpty()) subscribers.remove(subscriber.taskId, taskSubscribers);
        subscriber.cancelHeartbeat();
    }

    private final class Subscriber {
        private final String taskId;
        private final SseEmitter emitter;
        private final List<AgentEventRecord> pending = new ArrayList<>();
        private long lastSequence;
        private boolean replaying = true;
        private boolean closed;
        private final ScheduledFuture<?> heartbeat;

        private Subscriber(String taskId, SseEmitter emitter, long afterSequence) {
            this.taskId = taskId;
            this.emitter = emitter;
            this.lastSequence = afterSequence;
            this.heartbeat = resources.scheduler().scheduleAtFixedRate(
                    this::heartbeat,
                    15,
                    15,
                    TimeUnit.SECONDS);
        }

        private synchronized void replay(List<AgentEventRecord> events) {
            if (closed) return;
            List<AgentEventRecord> ordered = new ArrayList<>(events);
            ordered.addAll(pending);
            ordered.sort(Comparator.comparingLong(AgentEventRecord::sequence));
            pending.clear();
            for (AgentEventRecord event : ordered) send(event);
            replaying = false;
        }

        private synchronized void publish(AgentEventRecord event) {
            if (closed || event.sequence() <= lastSequence) return;
            if (replaying) pending.add(event);
            else send(event);
        }

        private void send(AgentEventRecord event) {
            if (closed || event.sequence() <= lastSequence) return;
            try {
                emitter.send(SseEmitter.event()
                        .id(Long.toString(event.sequence()))
                        .name(event.eventType())
                        .data(event));
                lastSequence = event.sequence();
            } catch (IOException | IllegalStateException error) {
                closed = true;
                emitter.complete();
                remove(this);
            }
        }

        private synchronized void heartbeat() {
            if (closed) return;
            try {
                emitter.send(SseEmitter.event().comment("keepalive"));
            } catch (IOException | IllegalStateException error) {
                close();
                remove(this);
            }
        }

        private synchronized void close() {
            if (closed) return;
            closed = true;
            cancelHeartbeat();
            emitter.complete();
        }

        private void cancelHeartbeat() {
            heartbeat.cancel(false);
        }
    }
}

package com.opendata.chatbot.job;

import com.opendata.chatbot.job.task.impl.OpenDataTaskImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

@Component
@Slf4j
@RequiredArgsConstructor
public class StartupRunner {

    private final OpenDataTaskImpl openDataTask;

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        CompletableFuture.runAsync(() -> {
            try {
                openDataTask.doRun();
            } catch (Exception e) {
                log.error("Startup task failed", e);
            }
        });
        log.info("StartupRunner: OpenDataTaskImpl scheduled in background.");
    }
}

package com.opendata.chatbot.job;

import com.opendata.chatbot.job.task.impl.OpenDataTaskImpl;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class StartupRunner {

    private final OpenDataTaskImpl openDataTask;

    public StartupRunner(OpenDataTaskImpl openDataTask) {
        this.openDataTask = openDataTask;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        try {
            // 🔥 啟動後立即執行一次
            openDataTask.doRun();
            System.out.println("StartupRunner: OpenDataTaskImpl executed on startup.");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

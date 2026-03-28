package com.opendata.chatbot.job;

import com.opendata.chatbot.job.task.OpenDataTask;
import lombok.extern.slf4j.Slf4j;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.quartz.QuartzJobBean;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class MessageJob extends QuartzJobBean {

    @Autowired
    private OpenDataTask openDataTaskImpl;

    @Override
    protected void executeInternal(JobExecutionContext jobExecutionContext) throws JobExecutionException {
        JobDataMap jobDataMap = jobExecutionContext.getJobDetail().getJobDataMap();

        log.info("Set quartz run => {}, Trigger => {}", jobDataMap.get("name"), jobExecutionContext.getTrigger());

        var jobGroupName = jobExecutionContext.getTrigger().getJobKey().getGroup();
        log.info("jobGroupName => {}", jobGroupName);

        if ("openDataTaskImpl".equals(jobGroupName)) {
            openDataTaskImpl.doRun();
        }

        ((OpenDataTask) jobDataMap.get("task")).doRun();
    }
}

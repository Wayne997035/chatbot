package com.opendata.chatbot.util;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.*;
import org.quartz.impl.matchers.GroupMatcher;
import org.springframework.scheduling.quartz.QuartzJobBean;
import org.springframework.stereotype.Service;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class QuartzUtils {

    private final Scheduler scheduler;

    @PostConstruct
    public void startScheduler() {
        try {
            scheduler.start();
        } catch (SchedulerException e) {
            log.error("Failed to start scheduler", e);
        }
    }

    /**
     * 增加一個job
     *
     * @param jobClass     任務實現類
     * @param jobName      任務名稱
     * @param jobGroupName 任務組名
     * @param jobTime      時間表達式 (這是每隔多少秒為一次任務)
     * @param jobTimes     運行的次數 （<0:表示不限次數）
     */
    public void addJob(Class<? extends QuartzJobBean> jobClass, String jobName, String jobGroupName,
                       int jobTime, int jobTimes, Map<String, String> jobData) {
        try {
            JobDetail jobDetail = JobBuilder.newJob(jobClass).withIdentity(jobName, jobGroupName).build();
            Trigger trigger;
            if (jobTimes < 0) {
                trigger = TriggerBuilder.newTrigger().withIdentity(jobName, jobGroupName)
                        .withSchedule(SimpleScheduleBuilder.repeatSecondlyForever(1)
                                .withIntervalInSeconds(jobTime))
                        .startNow().build();
            } else {
                trigger = TriggerBuilder.newTrigger().withIdentity(jobName, jobGroupName)
                        .withSchedule(SimpleScheduleBuilder.repeatSecondlyForever(1)
                                .withIntervalInSeconds(jobTime).withRepeatCount(jobTimes))
                        .startNow().build();
            }
            scheduler.scheduleJob(jobDetail, trigger);
        } catch (SchedulerException e) {
            log.error("Failed to add job [{}/{}]", jobName, jobGroupName, e);
        }
    }

    /**
     * 增加一個job
     *
     * @param jobClass     任務實現類
     * @param jobName      任務名稱
     * @param jobGroupName 任務組名
     * @param jobTime      時間表達式 （如：0/5 * * * * ? ）
     */
    public void addJob(Class<? extends QuartzJobBean> jobClass, String jobName, String jobGroupName,
                       String jobTime, Map<String, Object> jobData) {
        try {
            JobDetail jobDetail = JobBuilder.newJob(jobClass).withIdentity(jobName, jobGroupName).build();

            if (jobData != null && !jobData.isEmpty()) {
                jobDetail.getJobDataMap().putAll(jobData);
            }

            Trigger trigger = TriggerBuilder.newTrigger().withIdentity(jobName, jobGroupName)
                    .startAt(DateBuilder.futureDate(1, DateBuilder.IntervalUnit.SECOND))
                    .withSchedule(CronScheduleBuilder.cronSchedule(jobTime).withMisfireHandlingInstructionFireAndProceed())
                    .startNow().build();
            scheduler.scheduleJob(jobDetail, trigger);
        } catch (Exception e) {
            log.error("Failed to add cron job [{}/{}]", jobName, jobGroupName, e);
        }
    }

    /**
     * 修改 一個job的 時間表達式
     */
    public void updateJob(String jobName, String jobGroupName, String jobTime) {
        try {
            TriggerKey triggerKey = TriggerKey.triggerKey(jobName, jobGroupName);
            CronTrigger trigger = (CronTrigger) scheduler.getTrigger(triggerKey);
            trigger = trigger.getTriggerBuilder().withIdentity(triggerKey)
                    .withSchedule(CronScheduleBuilder.cronSchedule(jobTime)).build();
            scheduler.rescheduleJob(triggerKey, trigger);
        } catch (SchedulerException e) {
            log.error("Failed to update job [{}/{}]", jobName, jobGroupName, e);
        }
    }

    /**
     * 刪除任務一個job
     */
    public void deleteJob(String jobName, String jobGroupName) {
        try {
            scheduler.deleteJob(new JobKey(jobName, jobGroupName));
        } catch (Exception e) {
            log.error("Failed to delete job [{}/{}]", jobName, jobGroupName, e);
        }
    }

    /**
     * 暫停一個job
     */
    public void pauseJob(String jobName, String jobGroupName) {
        try {
            scheduler.pauseJob(JobKey.jobKey(jobName, jobGroupName));
        } catch (SchedulerException e) {
            log.error("Failed to pause job [{}/{}]", jobName, jobGroupName, e);
        }
    }

    /**
     * 恢復一個job
     */
    public void resumeJob(String jobName, String jobGroupName) {
        try {
            scheduler.resumeJob(JobKey.jobKey(jobName, jobGroupName));
        } catch (SchedulerException e) {
            log.error("Failed to resume job [{}/{}]", jobName, jobGroupName, e);
        }
    }

    /**
     * 立即執行一個job
     */
    public void runAJobNow(String jobName, String jobGroupName) {
        try {
            scheduler.triggerJob(JobKey.jobKey(jobName, jobGroupName));
        } catch (SchedulerException e) {
            log.error("Failed to trigger job [{}/{}]", jobName, jobGroupName, e);
        }
    }

    /**
     * 獲取所有計劃中的任務列表
     */
    public List<Map<String, Object>> queryAllJob() {
        List<Map<String, Object>> jobList = new ArrayList<>();
        try {
            GroupMatcher<JobKey> matcher = GroupMatcher.anyJobGroup();
            Set<JobKey> jobKeys = scheduler.getJobKeys(matcher);
            for (JobKey jobKey : jobKeys) {
                List<? extends Trigger> triggers = scheduler.getTriggersOfJob(jobKey);
                for (Trigger trigger : triggers) {
                    Map<String, Object> map = new HashMap<>();
                    map.put("jobName", jobKey.getName());
                    map.put("jobGroupName", jobKey.getGroup());
                    map.put("description", "觸發器:" + trigger.getKey());
                    map.put("jobStatus", scheduler.getTriggerState(trigger.getKey()).name());
                    if (trigger instanceof CronTrigger cronTrigger) {
                        map.put("jobTime", cronTrigger.getCronExpression());
                    }
                    jobList.add(map);
                }
            }
        } catch (SchedulerException e) {
            log.error("Failed to query all jobs", e);
        }
        return jobList;
    }

    /**
     * 獲取所有正在運行的job
     */
    public List<Map<String, Object>> queryRunJob() {
        List<Map<String, Object>> jobList = new ArrayList<>();
        try {
            List<JobExecutionContext> executingJobs = scheduler.getCurrentlyExecutingJobs();
            for (JobExecutionContext executingJob : executingJobs) {
                JobDetail jobDetail = executingJob.getJobDetail();
                JobKey jobKey = jobDetail.getKey();
                Trigger trigger = executingJob.getTrigger();
                Map<String, Object> map = new HashMap<>();
                map.put("jobName", jobKey.getName());
                map.put("jobGroupName", jobKey.getGroup());
                map.put("description", "觸發器:" + trigger.getKey());
                map.put("jobStatus", scheduler.getTriggerState(trigger.getKey()).name());
                if (trigger instanceof CronTrigger cronTrigger) {
                    map.put("jobTime", cronTrigger.getCronExpression());
                }
                jobList.add(map);
            }
        } catch (SchedulerException e) {
            log.error("Failed to query running jobs", e);
        }
        return jobList;
    }
}

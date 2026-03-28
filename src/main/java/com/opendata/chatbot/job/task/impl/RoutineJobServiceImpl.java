package com.opendata.chatbot.job.task.impl;

import com.opendata.chatbot.job.MessageJob;
import com.opendata.chatbot.job.task.OpenDataTask;
import com.opendata.chatbot.job.task.RoutineJobService;
import com.opendata.chatbot.util.QuartzUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.ClassUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class RoutineJobServiceImpl implements RoutineJobService {

    private final List<OpenDataTask> openDataTaskList;
    private final QuartzUtils quartzUtils;

    @Override
    public boolean addRoutineJob(String jobName, String jobGroupName, String jobTime) {

        log.info("addRoutineJob -> jobName :{}, jobGroupName: {}, jobTime:{}", jobName,
                jobGroupName, jobTime);

        Map<String, Object> jobData = new HashMap<>();
        jobData.put("name", jobName);

        Optional<OpenDataTask> crawlTaskOpt = this.setTask(jobGroupName);

        if (crawlTaskOpt.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Task no exist");
        }

        jobData.put("task", crawlTaskOpt.get());

        quartzUtils.addJob(MessageJob.class, jobName, jobGroupName, jobTime, jobData);

        log.info("add job finish ,jobData -> {} ", jobData);
        return true;
    }

    private Optional<OpenDataTask> setTask(String jobGroupName) {
        return openDataTaskList.stream()
                .filter(openDataJobTask -> ClassUtils.getUserClass(openDataJobTask).getSimpleName().equals(jobGroupName))
                .findFirst();
    }

}
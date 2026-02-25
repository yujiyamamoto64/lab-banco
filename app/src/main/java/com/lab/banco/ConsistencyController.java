package com.lab.banco;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ConsistencyController {

    private final ConsistencyMonitorService consistencyMonitorService;

    public ConsistencyController(ConsistencyMonitorService consistencyMonitorService) {
        this.consistencyMonitorService = consistencyMonitorService;
    }

    @GetMapping("/consistency")
    public ConsistencyMonitorService.ConsistencySnapshot getConsistencySnapshot() {
        return consistencyMonitorService.getSnapshot();
    }

    @GetMapping("/consistency/issues")
    public List<ConsistencyMonitorService.ConsistencyIssue> listConsistencyIssues() {
        return consistencyMonitorService.getSnapshot().recentIssues();
    }
}

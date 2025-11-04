package net.thekingofduck.ningan.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 异步任务服务
 * 用于处理长时间运行的文件操作任务
 */
@Service
public class AsyncTaskService {
    
    private static final Logger logger = LoggerFactory.getLogger(AsyncTaskService.class);
    
    // 任务状态存储
    private final Map<String, TaskProgress> taskProgressMap = new ConcurrentHashMap<>();
    
    // 任务ID生成器
    private final AtomicLong taskIdGenerator = new AtomicLong(1);
    
    /**
     * 任务进度信息
     */
    public static class TaskProgress {
        private String taskId;
        private String taskType;
        private String status; // RUNNING, COMPLETED, FAILED
        private int totalItems;
        private AtomicInteger completedItems = new AtomicInteger(0);
        private AtomicInteger failedItems = new AtomicInteger(0);
        private long startTime;
        private long endTime;
        private String errorMessage;
        private Map<String, Object> result = new HashMap<>();
        
        public TaskProgress(String taskId, String taskType, int totalItems) {
            this.taskId = taskId;
            this.taskType = taskType;
            this.totalItems = totalItems;
            this.status = "RUNNING";
            this.startTime = System.currentTimeMillis();
        }
        
        // Getters and setters
        public String getTaskId() { return taskId; }
        public String getTaskType() { return taskType; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public int getTotalItems() { return totalItems; }
        public int getCompletedItems() { return completedItems.get(); }
        public int getFailedItems() { return failedItems.get(); }
        public long getStartTime() { return startTime; }
        public long getEndTime() { return endTime; }
        public void setEndTime(long endTime) { this.endTime = endTime; }
        public String getErrorMessage() { return errorMessage; }
        public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
        public Map<String, Object> getResult() { return result; }
        public void setResult(Map<String, Object> result) { this.result = result; }
        
        public void incrementCompleted() { completedItems.incrementAndGet(); }
        public void incrementFailed() { failedItems.incrementAndGet(); }
        
        public double getProgress() {
            if (totalItems == 0) return 0.0;
            return (double) (completedItems.get() + failedItems.get()) / totalItems * 100.0;
        }
        
        public long getDuration() {
            if (endTime > 0) {
                return endTime - startTime;
            }
            return System.currentTimeMillis() - startTime;
        }
    }
    
    /**
     * 创建新任务
     */
    public String createTask(String taskType, int totalItems) {
        String taskId = "task_" + taskIdGenerator.getAndIncrement();
        TaskProgress progress = new TaskProgress(taskId, taskType, totalItems);
        taskProgressMap.put(taskId, progress);
        
        logger.info("创建异步任务 - ID: {}, 类型: {}, 总数: {}", taskId, taskType, totalItems);
        return taskId;
    }
    
    /**
     * 获取任务进度
     */
    public TaskProgress getTaskProgress(String taskId) {
        return taskProgressMap.get(taskId);
    }
    
    /**
     * 更新任务状态
     */
    public void updateTaskStatus(String taskId, String status) {
        TaskProgress progress = taskProgressMap.get(taskId);
        if (progress != null) {
            progress.setStatus(status);
            if ("COMPLETED".equals(status) || "FAILED".equals(status)) {
                progress.setEndTime(System.currentTimeMillis());
            }
        }
    }
    
    /**
     * 设置任务错误信息
     */
    public void setTaskError(String taskId, String errorMessage) {
        TaskProgress progress = taskProgressMap.get(taskId);
        if (progress != null) {
            progress.setErrorMessage(errorMessage);
            progress.setStatus("FAILED");
            progress.setEndTime(System.currentTimeMillis());
        }
    }
    
    /**
     * 设置任务结果
     */
    public void setTaskResult(String taskId, Map<String, Object> result) {
        TaskProgress progress = taskProgressMap.get(taskId);
        if (progress != null) {
            progress.setResult(result);
        }
    }
    
    /**
     * 增加完成计数
     */
    public void incrementCompleted(String taskId) {
        TaskProgress progress = taskProgressMap.get(taskId);
        if (progress != null) {
            progress.incrementCompleted();
        }
    }
    
    /**
     * 增加失败计数
     */
    public void incrementFailed(String taskId) {
        TaskProgress progress = taskProgressMap.get(taskId);
        if (progress != null) {
            progress.incrementFailed();
        }
    }
    
    /**
     * 清理过期任务（超过1小时的已完成任务）
     */
    public void cleanupExpiredTasks() {
        long currentTime = System.currentTimeMillis();
        long oneHour = 60 * 60 * 1000; // 1小时
        
        taskProgressMap.entrySet().removeIf(entry -> {
            TaskProgress progress = entry.getValue();
            return ("COMPLETED".equals(progress.getStatus()) || "FAILED".equals(progress.getStatus())) 
                   && (currentTime - progress.getEndTime()) > oneHour;
        });
    }
    
    /**
     * 获取所有任务状态
     */
    public Map<String, TaskProgress> getAllTasks() {
        return new HashMap<>(taskProgressMap);
    }
}
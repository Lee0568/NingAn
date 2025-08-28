package net.thekingofduck.loki.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 缓存服务
 * 用于缓存文件列表、连接信息等数据以提高性能
 */
@Service
public class CacheService {
    
    private static final Logger logger = LoggerFactory.getLogger(CacheService.class);
    
    // 缓存项
    private static class CacheItem<T> {
        private final T data;
        private final long timestamp;
        private final long ttl; // 生存时间（毫秒）
        
        public CacheItem(T data, long ttl) {
            this.data = data;
            this.timestamp = System.currentTimeMillis();
            this.ttl = ttl;
        }
        
        public T getData() {
            return data;
        }
        
        public boolean isExpired() {
            return System.currentTimeMillis() - timestamp > ttl;
        }
        
        public long getAge() {
            return System.currentTimeMillis() - timestamp;
        }
    }
    
    // 文件列表缓存（5分钟TTL）
    private final Map<String, CacheItem<List<Map<String, Object>>>> fileListCache = new ConcurrentHashMap<>();
    private static final long FILE_LIST_TTL = 5 * 60 * 1000; // 5分钟
    
    // 文件内容缓存（10分钟TTL）
    private final Map<String, CacheItem<String>> fileContentCache = new ConcurrentHashMap<>();
    private static final long FILE_CONTENT_TTL = 10 * 60 * 1000; // 10分钟
    
    // 连接状态缓存（1分钟TTL）
    private final Map<String, CacheItem<Boolean>> connectionStatusCache = new ConcurrentHashMap<>();
    private static final long CONNECTION_STATUS_TTL = 1 * 60 * 1000; // 1分钟
    
    // 定时清理器
    private final ScheduledExecutorService cleanupExecutor = Executors.newSingleThreadScheduledExecutor();
    
    public CacheService() {
        // 每5分钟清理一次过期缓存
        cleanupExecutor.scheduleAtFixedRate(this::cleanupExpiredItems, 5, 5, TimeUnit.MINUTES);
        logger.info("缓存服务已启动，定时清理任务已设置");
    }
    
    /**
     * 生成缓存键
     */
    private String generateCacheKey(String host, int port, String username, String path) {
        return String.format("%s:%d:%s:%s", host, port, username, path != null ? path : "");
    }
    
    /**
     * 缓存文件列表
     */
    public void cacheFileList(String host, int port, String username, String path, List<Map<String, Object>> fileList) {
        String key = generateCacheKey(host, port, username, path);
        fileListCache.put(key, new CacheItem<>(fileList, FILE_LIST_TTL));
        logger.debug("缓存文件列表 - 键: {}, 文件数量: {}", key, fileList.size());
    }
    
    /**
     * 获取缓存的文件列表
     */
    public List<Map<String, Object>> getCachedFileList(String host, int port, String username, String path) {
        String key = generateCacheKey(host, port, username, path);
        CacheItem<List<Map<String, Object>>> item = fileListCache.get(key);
        
        if (item != null && !item.isExpired()) {
            logger.debug("命中文件列表缓存 - 键: {}, 缓存年龄: {}ms", key, item.getAge());
            return item.getData();
        }
        
        if (item != null && item.isExpired()) {
            fileListCache.remove(key);
            logger.debug("文件列表缓存已过期并移除 - 键: {}", key);
        }
        
        return null;
    }
    
    /**
     * 缓存文件内容
     */
    public void cacheFileContent(String host, int port, String username, String filePath, String content) {
        String key = generateCacheKey(host, port, username, filePath);
        fileContentCache.put(key, new CacheItem<>(content, FILE_CONTENT_TTL));
        logger.debug("缓存文件内容 - 键: {}, 内容长度: {}", key, content.length());
    }
    
    /**
     * 获取缓存的文件内容
     */
    public String getCachedFileContent(String host, int port, String username, String filePath) {
        String key = generateCacheKey(host, port, username, filePath);
        CacheItem<String> item = fileContentCache.get(key);
        
        if (item != null && !item.isExpired()) {
            logger.debug("命中文件内容缓存 - 键: {}, 缓存年龄: {}ms", key, item.getAge());
            return item.getData();
        }
        
        if (item != null && item.isExpired()) {
            fileContentCache.remove(key);
            logger.debug("文件内容缓存已过期并移除 - 键: {}", key);
        }
        
        return null;
    }
    
    /**
     * 缓存连接状态
     */
    public void cacheConnectionStatus(String host, int port, String username, boolean isConnected) {
        String key = generateCacheKey(host, port, username, null);
        connectionStatusCache.put(key, new CacheItem<>(isConnected, CONNECTION_STATUS_TTL));
        logger.debug("缓存连接状态 - 键: {}, 状态: {}", key, isConnected);
    }
    
    /**
     * 获取缓存的连接状态
     */
    public Boolean getCachedConnectionStatus(String host, int port, String username) {
        String key = generateCacheKey(host, port, username, null);
        CacheItem<Boolean> item = connectionStatusCache.get(key);
        
        if (item != null && !item.isExpired()) {
            logger.debug("命中连接状态缓存 - 键: {}, 状态: {}, 缓存年龄: {}ms", key, item.getData(), item.getAge());
            return item.getData();
        }
        
        if (item != null && item.isExpired()) {
            connectionStatusCache.remove(key);
            logger.debug("连接状态缓存已过期并移除 - 键: {}", key);
        }
        
        return null;
    }
    
    /**
     * 清除指定连接的所有缓存
     */
    public void clearConnectionCache(String host, int port, String username) {
        String keyPrefix = String.format("%s:%d:%s:", host, port, username);
        
        // 清除文件列表缓存
        fileListCache.entrySet().removeIf(entry -> entry.getKey().startsWith(keyPrefix));
        
        // 清除文件内容缓存
        fileContentCache.entrySet().removeIf(entry -> entry.getKey().startsWith(keyPrefix));
        
        // 清除连接状态缓存
        String connectionKey = generateCacheKey(host, port, username, null);
        connectionStatusCache.remove(connectionKey);
        
        logger.info("已清除连接缓存 - 主机: {}:{}, 用户: {}", host, port, username);
    }
    
    /**
     * 清除指定路径的文件列表缓存
     */
    public void clearFileListCache(String host, int port, String username, String path) {
        String key = generateCacheKey(host, port, username, path);
        fileListCache.remove(key);
        logger.debug("已清除文件列表缓存 - 键: {}", key);
    }
    
    /**
     * 清除指定文件的内容缓存
     */
    public void clearFileContentCache(String host, int port, String username, String filePath) {
        String key = generateCacheKey(host, port, username, filePath);
        fileContentCache.remove(key);
        logger.debug("已清除文件内容缓存 - 键: {}", key);
    }
    
    /**
     * 清理所有过期缓存项
     */
    public void cleanupExpiredItems() {
        int removedFileList = 0;
        int removedFileContent = 0;
        int removedConnectionStatus = 0;
        
        // 清理文件列表缓存
        removedFileList = fileListCache.entrySet().removeIf(entry -> entry.getValue().isExpired()) ? 
                         fileListCache.size() : removedFileList;
        
        // 清理文件内容缓存
        removedFileContent = fileContentCache.entrySet().removeIf(entry -> entry.getValue().isExpired()) ? 
                            fileContentCache.size() : removedFileContent;
        
        // 清理连接状态缓存
        removedConnectionStatus = connectionStatusCache.entrySet().removeIf(entry -> entry.getValue().isExpired()) ? 
                                 connectionStatusCache.size() : removedConnectionStatus;
        
        if (removedFileList > 0 || removedFileContent > 0 || removedConnectionStatus > 0) {
            logger.info("清理过期缓存完成 - 文件列表: {}, 文件内容: {}, 连接状态: {}", 
                       removedFileList, removedFileContent, removedConnectionStatus);
        }
    }
    
    /**
     * 获取缓存统计信息
     */
    public Map<String, Object> getCacheStats() {
        Map<String, Object> stats = new ConcurrentHashMap<>();
        
        stats.put("fileListCacheSize", fileListCache.size());
        stats.put("fileContentCacheSize", fileContentCache.size());
        stats.put("connectionStatusCacheSize", connectionStatusCache.size());
        
        // 计算命中率（这里简化处理，实际应用中可以维护更详细的统计）
        stats.put("totalCacheSize", fileListCache.size() + fileContentCache.size() + connectionStatusCache.size());
        
        return stats;
    }
    
    /**
     * 清空所有缓存
     */
    public void clearAllCache() {
        fileListCache.clear();
        fileContentCache.clear();
        connectionStatusCache.clear();
        logger.info("已清空所有缓存");
    }
    
    /**
     * 关闭缓存服务
     */
    public void shutdown() {
        cleanupExecutor.shutdown();
        try {
            if (!cleanupExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                cleanupExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            cleanupExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        logger.info("缓存服务已关闭");
    }
}
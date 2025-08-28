package net.thekingofduck.loki.service;

import com.jcraft.jsch.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.PreDestroy;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * SSH连接管理器
 * 负责管理SSH连接的生命周期，包括连接创建、复用、清理等
 */
@Component
public class SshConnectionManager {
    
    private static final Logger logger = LoggerFactory.getLogger(SshConnectionManager.class);
    
    // 连接池
    private final Map<String, SshConnectionInfo> connectionPool = new ConcurrentHashMap<>();
    
    // 连接锁，防止并发创建相同连接
    private final Map<String, ReentrantLock> connectionLocks = new ConcurrentHashMap<>();
    
    // 连接超时时间（30分钟）
    private static final long CONNECTION_TIMEOUT = 30 * 60 * 1000;
    
    // 最大连接数
    private static final int MAX_CONNECTIONS = 50;
    
    /**
     * SSH连接信息封装类
     */
    public static class SshConnectionInfo {
        private final String connectionId;
        private final Session session;
        private final ChannelSftp sftpChannel;
        private final String host;
        private final int port;
        private final String username;
        private long lastUsed;
        private volatile boolean inUse;
        
        public SshConnectionInfo(String connectionId, Session session, ChannelSftp sftpChannel, 
                               String host, int port, String username) {
            this.connectionId = connectionId;
            this.session = session;
            this.sftpChannel = sftpChannel;
            this.host = host;
            this.port = port;
            this.username = username;
            this.lastUsed = System.currentTimeMillis();
            this.inUse = false;
        }
        
        public void updateLastUsed() {
            this.lastUsed = System.currentTimeMillis();
        }
        
        public boolean isExpired() {
            return System.currentTimeMillis() - lastUsed > CONNECTION_TIMEOUT;
        }
        
        public boolean isValid() {
            return session != null && session.isConnected() && 
                   sftpChannel != null && sftpChannel.isConnected();
        }
        
        public void close() {
            try {
                if (sftpChannel != null && sftpChannel.isConnected()) {
                    sftpChannel.disconnect();
                }
                if (session != null && session.isConnected()) {
                    session.disconnect();
                }
                logger.info("SSH连接已关闭: {}@{}:{}", username, host, port);
            } catch (Exception e) {
                logger.error("关闭SSH连接时发生错误: {}", e.getMessage());
            }
        }
        
        // Getters
        public String getConnectionId() { return connectionId; }
        public Session getSession() { return session; }
        public ChannelSftp getSftpChannel() { return sftpChannel; }
        public String getHost() { return host; }
        public int getPort() { return port; }
        public String getUsername() { return username; }
        public long getLastUsed() { return lastUsed; }
        public boolean isInUse() { return inUse; }
        public void setInUse(boolean inUse) { this.inUse = inUse; }
    }
    
    /**
     * 获取或创建SSH连接
     */
    public SshConnectionInfo getConnection(String host, int port, String username, String password) throws Exception {
        String connectionKey = generateConnectionKey(host, port, username);
        
        // 检查连接池中是否有可用连接
        SshConnectionInfo existingConnection = connectionPool.get(connectionKey);
        if (existingConnection != null && existingConnection.isValid() && !existingConnection.isExpired()) {
            existingConnection.updateLastUsed();
            existingConnection.setInUse(true);
            logger.debug("复用现有SSH连接: {}@{}:{}", username, host, port);
            return existingConnection;
        }
        
        // 获取连接锁，防止并发创建相同连接
        ReentrantLock lock = connectionLocks.computeIfAbsent(connectionKey, k -> new ReentrantLock());
        lock.lock();
        
        try {
            // 再次检查，可能在等待锁的过程中其他线程已经创建了连接
            existingConnection = connectionPool.get(connectionKey);
            if (existingConnection != null && existingConnection.isValid() && !existingConnection.isExpired()) {
                existingConnection.updateLastUsed();
                existingConnection.setInUse(true);
                return existingConnection;
            }
            
            // 检查连接数限制
            if (connectionPool.size() >= MAX_CONNECTIONS) {
                cleanupExpiredConnections();
                if (connectionPool.size() >= MAX_CONNECTIONS) {
                    throw new Exception("连接数已达到最大限制: " + MAX_CONNECTIONS);
                }
            }
            
            // 创建新连接
            SshConnectionInfo newConnection = createConnection(host, port, username, password);
            connectionPool.put(connectionKey, newConnection);
            newConnection.setInUse(true);
            
            logger.info("创建新SSH连接: {}@{}:{}", username, host, port);
            return newConnection;
            
        } finally {
            lock.unlock();
        }
    }
    
    /**
     * 创建SSH连接
     */
    private SshConnectionInfo createConnection(String host, int port, String username, String password) throws Exception {
        JSch jsch = new JSch();
        Session session = null;
        ChannelSftp sftpChannel = null;
        
        try {
            // 创建会话
            session = jsch.getSession(username, host, port);
            session.setPassword(password);
            session.setConfig("StrictHostKeyChecking", "no");
            session.setConfig("PreferredAuthentications", "password");
            session.setConfig("UseDNS", "no");
            
            // 设置超时时间
            session.connect(30000); // 30秒连接超时
            
            // 创建SFTP通道
            sftpChannel = (ChannelSftp) session.openChannel("sftp");
            sftpChannel.connect(10000); // 10秒通道连接超时
            
            String connectionId = java.util.UUID.randomUUID().toString();
            return new SshConnectionInfo(connectionId, session, sftpChannel, host, port, username);
            
        } catch (Exception e) {
            // 连接失败时清理资源
            if (sftpChannel != null && sftpChannel.isConnected()) {
                sftpChannel.disconnect();
            }
            if (session != null && session.isConnected()) {
                session.disconnect();
            }
            throw new Exception("SSH连接失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 释放连接（标记为不在使用中）
     */
    public void releaseConnection(SshConnectionInfo connection) {
        if (connection != null) {
            connection.setInUse(false);
            connection.updateLastUsed();
            logger.debug("释放SSH连接: {}@{}:{}", connection.getUsername(), connection.getHost(), connection.getPort());
        }
    }
    
    /**
     * 关闭指定连接
     */
    public void closeConnection(String host, int port, String username) {
        String connectionKey = generateConnectionKey(host, port, username);
        SshConnectionInfo connection = connectionPool.remove(connectionKey);
        if (connection != null) {
            connection.close();
        }
    }
    
    /**
     * 生成连接键
     */
    private String generateConnectionKey(String host, int port, String username) {
        return String.format("%s:%d@%s", host, port, username);
    }
    
    /**
     * 定时清理过期连接（每5分钟执行一次）
     */
    @Scheduled(fixedRate = 5 * 60 * 1000)
    public void cleanupExpiredConnections() {
        logger.debug("开始清理过期SSH连接...");
        
        int cleanedCount = 0;
        for (Map.Entry<String, SshConnectionInfo> entry : connectionPool.entrySet()) {
            SshConnectionInfo connection = entry.getValue();
            
            // 清理过期或无效的连接（但不清理正在使用的连接）
            if (!connection.isInUse() && (connection.isExpired() || !connection.isValid())) {
                connectionPool.remove(entry.getKey());
                connection.close();
                cleanedCount++;
            }
        }
        
        if (cleanedCount > 0) {
            logger.info("清理了 {} 个过期SSH连接，当前连接数: {}", cleanedCount, connectionPool.size());
        }
    }
    
    /**
     * 获取连接池状态信息
     */
    public Map<String, Object> getConnectionPoolStatus() {
        Map<String, Object> status = new java.util.HashMap<>();
        status.put("totalConnections", connectionPool.size());
        status.put("maxConnections", MAX_CONNECTIONS);
        
        long activeConnections = connectionPool.values().stream()
                .mapToLong(conn -> conn.isInUse() ? 1 : 0)
                .sum();
        status.put("activeConnections", activeConnections);
        status.put("idleConnections", connectionPool.size() - activeConnections);
        
        return status;
    }
    
    /**
     * 应用关闭时清理所有连接
     */
    @PreDestroy
    public void shutdown() {
        logger.info("正在关闭SSH连接管理器，清理所有连接...");
        
        for (SshConnectionInfo connection : connectionPool.values()) {
            connection.close();
        }
        
        connectionPool.clear();
        connectionLocks.clear();
        
        logger.info("SSH连接管理器已关闭");
    }
}
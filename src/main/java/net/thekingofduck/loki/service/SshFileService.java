package net.thekingofduck.loki.service;

import com.jcraft.jsch.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Pattern;

@Service
public class SshFileService {
    
    @Autowired
    private SshConnectionManager connectionManager;
    
    @Autowired
    private CacheService cacheService;
    
    // 安全配置
    @Value("${ssh.file.max-upload-size:104857600}") // 默认100MB
    private long maxUploadSize;
    
    @Value("${ssh.file.max-download-size:524288000}") // 默认500MB
    private long maxDownloadSize;
    
    @Value("${ssh.file.max-preview-size:1048576}") // 默认1MB
    private long maxPreviewSize;
    
    // 允许的文件扩展名（空表示允许所有）
    @Value("${ssh.file.allowed-extensions:}")
    private String allowedExtensions;
    
    // 禁止的文件扩展名
    @Value("${ssh.file.blocked-extensions:.exe,.bat,.cmd,.com,.scr,.pif,.vbs,.js,.jar,.sh}")
    private String blockedExtensions;
    
    // 危险路径模式
    private static final List<Pattern> DANGEROUS_PATH_PATTERNS = Arrays.asList(
        Pattern.compile(".*\\.\\..*"), // 路径遍历
        Pattern.compile(".*/etc/passwd.*"), // 系统敏感文件
        Pattern.compile(".*/etc/shadow.*"),
        Pattern.compile(".*/proc/.*"),
        Pattern.compile(".*/sys/.*"),
        Pattern.compile(".*\\.ssh/.*"), // SSH密钥文件
        Pattern.compile(".*\\.aws/.*"), // AWS凭证
        Pattern.compile(".*\\.docker/.*") // Docker配置
    );
    

    
    /**
     * 连接SSH服务器
     */
    public String connect(String host, int port, String username, String password) throws Exception {
        SshConnectionManager.SshConnectionInfo connection = connectionManager.getConnection(host, port, username, password);
        return connection.getConnectionId();
    }
    
    /**
     * 断开SSH连接
     */
    public void disconnect(String host, int port, String username) {
        connectionManager.closeConnection(host, port, username);
    }
    
    /**
     * 获取SFTP通道
     */
    private SshConnectionManager.SshConnectionInfo getConnection(String host, int port, String username, String password) throws Exception {
        return connectionManager.getConnection(host, port, username, password);
    }
    
    /**
     * 释放连接
     */
    private void releaseConnection(SshConnectionManager.SshConnectionInfo connection) {
        connectionManager.releaseConnection(connection);
    }
    
    /**
     * 验证上传文件
     */
    private void validateUploadFile(MultipartFile file) throws Exception {
        if (file == null || file.isEmpty()) {
            throw new Exception("文件不能为空");
        }
        
        // 检查文件大小
        if (file.getSize() > maxUploadSize) {
            throw new Exception("文件太大，无法上传。最大允许大小: " + (maxUploadSize / 1024 / 1024) + "MB");
        }
        
        // 检查文件类型
        String filename = file.getOriginalFilename();
        if (filename == null || filename.trim().isEmpty()) {
            throw new Exception("文件名不能为空");
        }
        
        String extension = getFileExtension(filename).toLowerCase();
        
        // 检查是否为被禁止的文件类型
        if (!blockedExtensions.isEmpty()) {
            String[] blockedExts = blockedExtensions.split(",");
            for (String blockedExt : blockedExts) {
                if (extension.equals(blockedExt.trim().toLowerCase())) {
                    throw new Exception("不允许上传此类型的文件: " + extension);
                }
            }
        }
        
        // 如果设置了允许的文件类型，检查是否在允许列表中
        if (!allowedExtensions.isEmpty()) {
            boolean allowed = false;
            String[] allowedExts = allowedExtensions.split(",");
            for (String allowedExt : allowedExts) {
                if (extension.equals(allowedExt.trim().toLowerCase())) {
                    allowed = true;
                    break;
                }
            }
            if (!allowed) {
                throw new Exception("不允许上传此类型的文件: " + extension);
            }
        }
    }
    
    /**
     * 验证路径安全性
     */
    private void validatePath(String path) throws Exception {
        if (path == null || path.trim().isEmpty()) {
            throw new Exception("路径不能为空");
        }
        
        // 检查危险路径模式
        for (Pattern dangerousPattern : DANGEROUS_PATH_PATTERNS) {
            if (dangerousPattern.matcher(path).matches()) {
                throw new Exception("路径包含危险模式: " + dangerousPattern.pattern());
            }
        }
        
        // 检查路径遍历攻击
        String normalizedPath = path.replace("\\", "/");
        if (normalizedPath.contains("../") || normalizedPath.contains("/..") || 
            normalizedPath.equals("..") || normalizedPath.startsWith("../")) {
            throw new Exception("检测到路径遍历攻击");
        }
    }
    
    /**
     * 验证删除路径的安全性
     */
    private void validateDeletionPath(String path) throws Exception {
        // 禁止删除系统重要目录
        String[] protectedPaths = {
            "/", "/bin", "/boot", "/dev", "/etc", "/lib", "/proc", "/root", "/sbin", "/sys", "/usr", "/var",
            "C:\\", "C:\\Windows", "C:\\Program Files", "C:\\Program Files (x86)", "C:\\System32"
        };
        
        String normalizedPath = path.replace("\\", "/").toLowerCase();
        for (String protectedPath : protectedPaths) {
            String normalizedProtected = protectedPath.replace("\\", "/").toLowerCase();
            if (normalizedPath.equals(normalizedProtected) || normalizedPath.startsWith(normalizedProtected + "/")) {
                throw new Exception("禁止删除系统重要目录: " + path);
            }
        }
    }
    
    /**
     * 清理文件名，移除危险字符
     */
    private String sanitizeFileName(String filename) {
        if (filename == null) {
            return "";
        }
        
        // 移除或替换危险字符
        return filename.replaceAll("[<>:\"/\\\\|?*]", "_")
                      .replaceAll("\\.\\.+", "_")
                      .trim();
    }
    
    /**
     * 获取文件扩展名
     */
    private String getFileExtension(String filename) {
        if (filename == null || filename.isEmpty()) {
            return "";
        }
        
        int lastDotIndex = filename.lastIndexOf('.');
        if (lastDotIndex == -1 || lastDotIndex == filename.length() - 1) {
            return "";
        }
        
        return filename.substring(lastDotIndex + 1);
    }
    
    /**
     * 列出文件和目录
     */
    public List<Map<String, Object>> listFiles(String host, int port, String username, String password, String path) throws Exception {
        // 先检查缓存
        List<Map<String, Object>> cachedFiles = cacheService.getCachedFileList(host, port, username, path);
        if (cachedFiles != null) {
            return cachedFiles;
        }
        
        SshConnectionManager.SshConnectionInfo connection = getConnection(host, port, username, password);
        List<Map<String, Object>> files = new ArrayList<>();
        
        try {
            ChannelSftp sftpChannel = connection.getSftpChannel();
            Vector<ChannelSftp.LsEntry> entries = sftpChannel.ls(path);
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            
            for (ChannelSftp.LsEntry entry : entries) {
                String filename = entry.getFilename();
                // 跳过当前目录和父目录
                if (".".equals(filename) || "..".equals(filename)) {
                    continue;
                }
                
                SftpATTRS attrs = entry.getAttrs();
                Map<String, Object> fileInfo = new HashMap<>();
                
                fileInfo.put("name", filename);
                fileInfo.put("path", path.endsWith("/") ? path + filename : path + "/" + filename);
                fileInfo.put("type", attrs.isDir() ? "directory" : "file");
                fileInfo.put("size", attrs.getSize());
                fileInfo.put("modifyTime", sdf.format(new Date(attrs.getMTime() * 1000L)));
                fileInfo.put("permissions", attrs.getPermissionsString());
                fileInfo.put("owner", attrs.getUId());
                fileInfo.put("group", attrs.getGId());
                
                files.add(fileInfo);
            }
            
            // 按类型和名称排序：目录在前，文件在后，同类型按名称排序
            files.sort((a, b) -> {
                String typeA = (String) a.get("type");
                String typeB = (String) b.get("type");
                
                if (!typeA.equals(typeB)) {
                    return "directory".equals(typeA) ? -1 : 1;
                }
                
                String nameA = (String) a.get("name");
                String nameB = (String) b.get("name");
                return nameA.compareToIgnoreCase(nameB);
            });
            
            // 缓存结果
            cacheService.cacheFileList(host, port, username, path, files);
            
        } catch (SftpException e) {
            throw new Exception("获取文件列表失败: " + e.getMessage());
        } finally {
            releaseConnection(connection);
        }
        
        return files;
    }
    
    /**
     * 创建目录
     */
    public boolean createDirectory(String host, int port, String username, String password, String path, String name) throws Exception {
        // 安全检查
        validatePath(path);
        if (name == null || name.trim().isEmpty()) {
            throw new Exception("目录名不能为空");
        }
        
        // 清理目录名
        name = sanitizeFileName(name);
        String fullPath = path.endsWith("/") ? path + name : path + "/" + name;
        validatePath(fullPath);
        
        SshConnectionManager.SshConnectionInfo connection = getConnection(host, port, username, password);
        
        try {
            ChannelSftp sftpChannel = connection.getSftpChannel();
            
            // 检查目录是否已存在
            try {
                SftpATTRS attrs = sftpChannel.stat(fullPath);
                if (attrs != null) {
                    throw new Exception("目录已存在: " + name);
                }
            } catch (SftpException e) {
                // 目录不存在，可以继续创建
            }
            
            sftpChannel.mkdir(fullPath);
            return true;
        } catch (SftpException e) {
            throw new Exception("创建目录失败: " + e.getMessage());
        } finally {
            releaseConnection(connection);
        }
    }
    
    /**
     * 上传文件
     */
    public boolean uploadFile(String host, int port, String username, String password, String path, MultipartFile file) throws Exception {
        // 安全检查
        validateUploadFile(file);
        validatePath(path);
        
        String fileName = file.getOriginalFilename();
        if (fileName == null || fileName.trim().isEmpty()) {
            throw new Exception("文件名不能为空");
        }
        
        // 清理文件名
        fileName = sanitizeFileName(fileName);
        String fullPath = path.endsWith("/") ? path + fileName : path + "/" + fileName;
        validatePath(fullPath);
        
        SshConnectionManager.SshConnectionInfo connection = getConnection(host, port, username, password);
        
        try (InputStream inputStream = file.getInputStream()) {
            ChannelSftp sftpChannel = connection.getSftpChannel();
            
            // 检查目标路径是否存在同名文件
            try {
                SftpATTRS attrs = sftpChannel.stat(fullPath);
                if (attrs != null) {
                    throw new Exception("目标路径已存在同名文件: " + fileName);
                }
            } catch (SftpException e) {
                // 文件不存在，可以继续上传
            }
            
            sftpChannel.put(inputStream, fullPath);
            return true;
        } catch (SftpException | IOException e) {
            throw new Exception("上传文件失败: " + e.getMessage());
        } finally {
            releaseConnection(connection);
        }
    }
    
    /**
     * 下载文件
     */
    public byte[] downloadFile(String host, int port, String username, String password, String filePath) throws Exception {
        // 安全检查
        validatePath(filePath);
        
        SshConnectionManager.SshConnectionInfo connection = getConnection(host, port, username, password);
        
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            ChannelSftp sftpChannel = connection.getSftpChannel();
            
            // 检查文件大小
            SftpATTRS attrs = sftpChannel.stat(filePath);
            if (attrs.getSize() > maxDownloadSize) {
                throw new Exception("文件太大，无法下载。最大允许大小: " + (maxDownloadSize / 1024 / 1024) + "MB");
            }
            
            sftpChannel.get(filePath, outputStream);
            return outputStream.toByteArray();
        } catch (SftpException | IOException e) {
            throw new Exception("下载文件失败: " + e.getMessage());
        } finally {
            releaseConnection(connection);
        }
    }
    
    /**
     * 删除文件或目录
     */
    public boolean deleteFile(String host, int port, String username, String password, String filePath) throws Exception {
        // 安全检查
        validatePath(filePath);
        validateDeletionPath(filePath);
        
        SshConnectionManager.SshConnectionInfo connection = getConnection(host, port, username, password);
        
        try {
            ChannelSftp sftpChannel = connection.getSftpChannel();
            SftpATTRS attrs = sftpChannel.stat(filePath);
            if (attrs.isDir()) {
                // 删除目录（递归删除）
                deleteDirectory(sftpChannel, filePath);
            } else {
                // 删除文件
                sftpChannel.rm(filePath);
            }
            return true;
        } catch (SftpException e) {
            throw new Exception("删除失败: " + e.getMessage());
        } finally {
            releaseConnection(connection);
        }
    }
    
    /**
     * 递归删除目录
     */
    private void deleteDirectory(ChannelSftp sftpChannel, String dirPath) throws SftpException {
        Vector<ChannelSftp.LsEntry> entries = sftpChannel.ls(dirPath);
        
        for (ChannelSftp.LsEntry entry : entries) {
            String filename = entry.getFilename();
            if (".".equals(filename) || "..".equals(filename)) {
                continue;
            }
            
            String fullPath = dirPath + "/" + filename;
            SftpATTRS attrs = entry.getAttrs();
            
            if (attrs.isDir()) {
                deleteDirectory(sftpChannel, fullPath);
            } else {
                sftpChannel.rm(fullPath);
            }
        }
        
        sftpChannel.rmdir(dirPath);
    }
    
    /**
     * 重命名文件或目录
     */
    public boolean renameFile(String host, int port, String username, String password, String oldPath, String newName) throws Exception {
        // 安全检查
        validatePath(oldPath);
        if (newName == null || newName.trim().isEmpty()) {
            throw new Exception("新名称不能为空");
        }
        
        // 清理新文件名
        newName = sanitizeFileName(newName);
        
        SshConnectionManager.SshConnectionInfo connection = getConnection(host, port, username, password);
        
        try {
            ChannelSftp sftpChannel = connection.getSftpChannel();
            String parentPath = oldPath.substring(0, oldPath.lastIndexOf('/'));
            String newPath = parentPath + "/" + newName;
            
            // 验证新路径
            validatePath(newPath);
            
            // 检查新路径是否已存在
            try {
                SftpATTRS attrs = sftpChannel.stat(newPath);
                if (attrs != null) {
                    throw new Exception("目标名称已存在: " + newName);
                }
            } catch (SftpException e) {
                // 文件不存在，可以继续重命名
            }
            
            sftpChannel.rename(oldPath, newPath);
            return true;
        } catch (SftpException e) {
            throw new Exception("重命名失败: " + e.getMessage());
        } finally {
            releaseConnection(connection);
        }
    }
    
    /**
     * 获取文件内容（用于预览文本文件）
     */
    public String getFileContent(String host, int port, String username, String password, String filePath) throws Exception {
        // 安全检查
        validatePath(filePath);
        
        // 尝试从缓存获取
        String cachedContent = cacheService.getCachedFileContent(host, port, username, filePath);
        if (cachedContent != null) {
            return cachedContent;
        }
        
        SshConnectionManager.SshConnectionInfo connection = getConnection(host, port, username, password);
        
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            ChannelSftp sftpChannel = connection.getSftpChannel();
            
            // 检查文件大小
            SftpATTRS attrs = sftpChannel.stat(filePath);
            if (attrs.getSize() > maxPreviewSize) {
                throw new Exception("文件太大，无法预览。最大允许大小: " + (maxPreviewSize / 1024) + "KB");
            }
            
            // 检查是否为文本文件
            String extension = getFileExtension(filePath).toLowerCase();
            String[] textExtensions = {"txt", "log", "md", "json", "xml", "html", "css", "js", "java", "py", "cpp", "c", "h", "php", "sql", "yml", "yaml", "properties", "conf", "cfg", "ini"};
            boolean isTextFile = false;
            for (String textExt : textExtensions) {
                if (extension.equals(textExt)) {
                    isTextFile = true;
                    break;
                }
            }
            
            if (!isTextFile) {
                throw new Exception("不支持预览此类型的文件: " + extension);
            }
            
            sftpChannel.get(filePath, outputStream);
            
            // 限制文件大小，避免内存溢出（最大1MB）
            byte[] data = outputStream.toByteArray();
            if (data.length > 1024 * 1024) {
                throw new Exception("文件太大，无法预览");
            }
            
            String content = new String(data, "UTF-8");
            
            // 缓存文件内容
            cacheService.cacheFileContent(host, port, username, filePath, content);
            
            return content;
        } catch (SftpException | IOException e) {
            throw new Exception("获取文件内容失败: " + e.getMessage());
        } finally {
            releaseConnection(connection);
        }
    }

    /**
     * 保存文本文件内容
     */
    public boolean saveFileContent(String host, int port, String username, String password, String filePath, String content) throws Exception {
        validatePath(filePath);

        if (content == null) {
            content = "";
        }

        // 仅允许文本类型文件被编辑
        String extension = getFileExtension(filePath).toLowerCase();
        String[] textExtensions = {"txt", "log", "md", "json", "xml", "html", "css", "js", "java", "py", "cpp", "c", "h", "php", "sql", "ssh", "yml", "yaml", "properties", "conf", "cfg", "ini"};
        boolean isTextFile = false;
        for (String textExt : textExtensions) {
            if (extension.equals(textExt)) {
                isTextFile = true;
                break;
            }
        }
        if (!isTextFile) {
            throw new Exception("不支持编辑此类型的文件: " + extension);
        }

        byte[] data = content.getBytes("UTF-8");
        if (data.length > 1024 * 1024) { // 限制保存大小为1MB，避免异常大文本导致性能问题
            throw new Exception("文件内容过大，最大允许保存: 1MB");
        }

        SshConnectionManager.SshConnectionInfo connection = getConnection(host, port, username, password);
        try (java.io.InputStream inputStream = new java.io.ByteArrayInputStream(data)) {
            ChannelSftp sftpChannel = connection.getSftpChannel();
            sftpChannel.put(inputStream, filePath);

            // 清理相关缓存
            cacheService.clearFileContentCache(host, port, username, filePath);
            String parentPath = filePath.contains("/") ? filePath.substring(0, filePath.lastIndexOf('/')) : "/";
            cacheService.clearFileListCache(host, port, username, parentPath);
            return true;
        } catch (SftpException e) {
            throw new Exception("保存文件失败: " + e.getMessage());
        } finally {
            releaseConnection(connection);
        }
    }
    
    /**
     * 下载文件到输出流（用于批量下载）
     */
    public void downloadFileToStream(String host, int port, String username, String password, String filePath, java.io.OutputStream outputStream) throws Exception {
        // 安全检查
        validatePath(filePath);
        
        SshConnectionManager.SshConnectionInfo connection = getConnection(host, port, username, password);
        
        try {
            ChannelSftp sftpChannel = connection.getSftpChannel();
            
            // 检查文件大小
            SftpATTRS attrs = sftpChannel.stat(filePath);
            if (attrs.getSize() > maxDownloadSize) {
                throw new Exception("文件太大，无法下载。最大允许大小: " + (maxDownloadSize / 1024 / 1024) + "MB");
            }
            
            sftpChannel.get(filePath, outputStream);
        } catch (SftpException e) {
            throw new Exception("下载文件失败: " + e.getMessage());
        } finally {
            releaseConnection(connection);
        }
    }
    
    /**
     * 获取连接池状态
     */
    public Map<String, Object> getConnectionPoolStatus() {
        return connectionManager.getConnectionPoolStatus();
    }
}
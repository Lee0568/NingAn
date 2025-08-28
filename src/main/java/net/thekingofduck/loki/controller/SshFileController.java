package net.thekingofduck.loki.controller;

import net.thekingofduck.loki.service.SshFileService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * SSH文件操作控制器
 * 专门处理SSH文件相关操作，不包含服务器管理功能
 */
@RestController
@RequestMapping("/api/ssh")
@CrossOrigin(origins = "*")
public class SshFileController {
    
    private static final Logger logger = LoggerFactory.getLogger(SshFileController.class);
    
    @Autowired
    private SshFileService sshFileService;
    
    /**
     * 列出目录文件
     */
    @PostMapping("/listFiles")
    public ResponseEntity<?> listFiles(@RequestBody Map<String, Object> request, HttpServletRequest httpRequest) {
        return getFiles(request, httpRequest);
    }
    
    /**
     * 列出目录文件 (前端兼容接口)
     */
    @PostMapping("/files")
    public ResponseEntity<?> getFiles(@RequestBody Map<String, Object> request, HttpServletRequest httpRequest) {
        String clientIp = getClientIpAddress(httpRequest);
        logger.info("列出文件请求 - 客户端IP: {}", clientIp);
        
        try {
            String host = (String) request.get("host");
            Integer port = (Integer) request.get("port");
            String username = (String) request.get("username");
            String password = (String) request.get("password");
            String path = (String) request.get("path");
            
            if (host == null || port == null || username == null || password == null) {
                logger.warn("列出文件失败 - 缺少必要参数, 客户端IP: {}", clientIp);
                return ResponseEntity.badRequest().body(Map.of("success", false, "message", "缺少必要参数", "code", "MISSING_PARAMS"));
            }
            
            if (path == null || path.trim().isEmpty()) {
                path = "/";
            }
            
            logger.info("开始列出文件 - 主机: {}:{}, 用户: {}, 路径: {}, 客户端IP: {}", host, port, username, path, clientIp);
            List<Map<String, Object>> files = sshFileService.listFiles(host, port, username, password, path);
            
            logAuditEvent("LIST_FILES", host, port, username, "路径: " + path + ", 文件数量: " + files.size(), clientIp, true);
            logger.info("列出文件成功 - 主机: {}:{}, 路径: {}, 文件数量: {}, 客户端IP: {}", host, port, path, files.size(), clientIp);
            
            return ResponseEntity.ok(Map.of("success", true, "data", files, "message", "获取文件列表成功", "code", "SUCCESS"));
            
        } catch (Exception e) {
            logger.error("列出文件失败 - 客户端IP: {}, 错误: {}", clientIp, e.getMessage(), e);
            return ResponseEntity.ok(Map.of("success", false, "message", "获取文件列表失败: " + e.getMessage(), "code", "LIST_FILES_FAILED"));
        }
    }
    
    /**
     * 上传文件
     */
    @PostMapping("/upload")
    public ResponseEntity<?> uploadFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam("host") String host,
            @RequestParam("port") Integer port,
            @RequestParam("username") String username,
            @RequestParam("password") String password,
            @RequestParam("remotePath") String remotePath,
            HttpServletRequest httpRequest) {
        
        String clientIp = getClientIpAddress(httpRequest);
        logger.info("上传文件请求 - 客户端IP: {}", clientIp);
        
        try {
            if (file.isEmpty()) {
                logger.warn("上传文件失败 - 文件为空, 客户端IP: {}", clientIp);
                return ResponseEntity.badRequest().body(Map.of("success", false, "message", "文件不能为空", "code", "EMPTY_FILE"));
            }
            
            if (host == null || port == null || username == null || password == null || remotePath == null) {
                logger.warn("上传文件失败 - 缺少必要参数, 客户端IP: {}", clientIp);
                return ResponseEntity.badRequest().body(Map.of("success", false, "message", "缺少必要参数", "code", "MISSING_PARAMS"));
            }
            
            String fileName = file.getOriginalFilename();
            logger.info("开始上传文件 - 主机: {}:{}, 用户: {}, 文件: {}, 远程路径: {}, 客户端IP: {}", host, port, username, fileName, remotePath, clientIp);
            
            sshFileService.uploadFile(host, port, username, password, remotePath, file);
            
            logAuditEvent("UPLOAD_FILE", host, port, username, "文件: " + fileName + ", 远程路径: " + remotePath + ", 大小: " + file.getSize() + "字节", clientIp, true);
            logger.info("上传文件成功 - 主机: {}:{}, 文件: {}, 远程路径: {}, 大小: {}字节, 客户端IP: {}", host, port, fileName, remotePath, file.getSize(), clientIp);
            
            return ResponseEntity.ok(Map.of("success", true, "message", "文件上传成功", "code", "SUCCESS"));
            
        } catch (Exception e) {
            logger.error("上传文件失败 - 客户端IP: {}, 错误: {}", clientIp, e.getMessage(), e);
            return ResponseEntity.ok(Map.of("success", false, "message", "文件上传失败: " + e.getMessage(), "code", "UPLOAD_FAILED"));
        }
    }
    
    /**
     * 下载文件
     */
    @PostMapping("/download")
    public ResponseEntity<?> downloadFile(@RequestBody Map<String, Object> request, HttpServletRequest httpRequest) {
        String clientIp = getClientIpAddress(httpRequest);
        logger.info("下载文件请求 - 客户端IP: {}", clientIp);
        
        try {
            String host = (String) request.get("host");
            Integer port = (Integer) request.get("port");
            String username = (String) request.get("username");
            String password = (String) request.get("password");
            String filePath = (String) request.get("filePath");
            
            if (host == null || port == null || username == null || password == null || filePath == null) {
                logger.warn("下载文件失败 - 缺少必要参数, 客户端IP: {}", clientIp);
                return ResponseEntity.badRequest().body(Map.of("success", false, "message", "缺少必要参数", "code", "MISSING_PARAMS"));
            }
            
            logger.info("开始下载文件 - 主机: {}:{}, 用户: {}, 路径: {}, 客户端IP: {}", host, port, username, filePath, clientIp);
            byte[] fileData = sshFileService.downloadFile(host, port, username, password, filePath);
            
            String fileName = getFileName(filePath);
            
            logAuditEvent("DOWNLOAD_FILE", host, port, username, "路径: " + filePath + ", 大小: " + fileData.length + "字节", clientIp, true);
            logger.info("下载文件成功 - 主机: {}:{}, 路径: {}, 大小: {}字节, 客户端IP: {}", host, port, filePath, fileData.length, clientIp);
            
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(fileData);
            
        } catch (Exception e) {
            logger.error("下载文件失败 - 客户端IP: {}, 错误: {}", clientIp, e.getMessage(), e);
            return ResponseEntity.ok(Map.of("success", false, "message", "文件下载失败: " + e.getMessage(), "code", "DOWNLOAD_FAILED"));
        }
    }
    
    /**
     * 删除文件
     */
    @PostMapping("/delete")
    public ResponseEntity<?> deleteFile(@RequestBody Map<String, Object> request, HttpServletRequest httpRequest) {
        String clientIp = getClientIpAddress(httpRequest);
        logger.info("删除文件请求 - 客户端IP: {}", clientIp);
        
        try {
            String host = (String) request.get("host");
            Integer port = (Integer) request.get("port");
            String username = (String) request.get("username");
            String password = (String) request.get("password");
            String filePath = (String) request.get("filePath");
            
            if (host == null || port == null || username == null || password == null || filePath == null) {
                logger.warn("删除文件失败 - 缺少必要参数, 客户端IP: {}", clientIp);
                return ResponseEntity.badRequest().body(Map.of("success", false, "message", "缺少必要参数", "code", "MISSING_PARAMS"));
            }
            
            logger.info("开始删除文件 - 主机: {}:{}, 用户: {}, 路径: {}, 客户端IP: {}", host, port, username, filePath, clientIp);
            sshFileService.deleteFile(host, port, username, password, filePath);
            
            logAuditEvent("DELETE_FILE", host, port, username, "路径: " + filePath, clientIp, true);
            logger.info("删除文件成功 - 主机: {}:{}, 路径: {}, 客户端IP: {}", host, port, filePath, clientIp);
            
            return ResponseEntity.ok(Map.of("success", true, "message", "文件删除成功", "code", "SUCCESS"));
            
        } catch (Exception e) {
            logger.error("删除文件失败 - 客户端IP: {}, 错误: {}", clientIp, e.getMessage(), e);
            return ResponseEntity.ok(Map.of("success", false, "message", "文件删除失败: " + e.getMessage(), "code", "DELETE_FAILED"));
        }
    }
    
    /**
     * 获取文件内容
     */
    @PostMapping("/getContent")
    public ResponseEntity<?> getFileContent(@RequestBody Map<String, Object> request, HttpServletRequest httpRequest) {
        String clientIp = getClientIpAddress(httpRequest);
        logger.info("获取文件内容请求 - 客户端IP: {}", clientIp);
        
        try {
            String host = (String) request.get("host");
            Integer port = (Integer) request.get("port");
            String username = (String) request.get("username");
            String password = (String) request.get("password");
            String filePath = (String) request.get("filePath");
            
            if (host == null || port == null || username == null || password == null || filePath == null) {
                logger.warn("获取文件内容失败 - 缺少必要参数, 客户端IP: {}", clientIp);
                return ResponseEntity.badRequest().body(Map.of("success", false, "message", "缺少必要参数", "code", "MISSING_PARAMS"));
            }
            
            logger.info("开始获取文件内容 - 主机: {}:{}, 用户: {}, 路径: {}, 客户端IP: {}", host, port, username, filePath, clientIp);
            String content = sshFileService.getFileContent(host, port, username, password, filePath);
            
            logAuditEvent("GET_FILE_CONTENT", host, port, username, "路径: " + filePath + ", 内容长度: " + content.length() + "字符", clientIp, true);
            logger.info("获取文件内容成功 - 主机: {}:{}, 路径: {}, 内容长度: {}字符, 客户端IP: {}", host, port, filePath, content.length(), clientIp);
            
            return ResponseEntity.ok(Map.of("success", true, "data", content, "message", "获取文件内容成功", "code", "SUCCESS"));
            
        } catch (Exception e) {
            logger.error("获取文件内容失败 - 客户端IP: {}, 错误: {}", clientIp, e.getMessage(), e);
            return ResponseEntity.ok(Map.of("success", false, "message", "获取文件内容失败: " + e.getMessage(), "code", "GET_CONTENT_FAILED"));
        }
    }
    
    /**
     * 文件预览
     */
    @PostMapping("/preview")
    public ResponseEntity<?> previewFile(@RequestBody Map<String, Object> request, HttpServletRequest httpRequest) {
        String clientIp = getClientIpAddress(httpRequest);
        logger.info("文件预览请求 - 客户端IP: {}", clientIp);
        
        try {
            String host = (String) request.get("host");
            Integer port = (Integer) request.get("port");
            String username = (String) request.get("username");
            String password = (String) request.get("password");
            String filePath = (String) request.get("filePath");
            
            if (host == null || port == null || username == null || password == null || filePath == null) {
                logger.warn("文件预览失败 - 缺少必要参数, 客户端IP: {}", clientIp);
                return ResponseEntity.badRequest().body(Map.of("success", false, "message", "缺少必要参数", "code", "MISSING_PARAMS"));
            }
            
            logger.info("开始预览文件 - 主机: {}:{}, 用户: {}, 路径: {}, 客户端IP: {}", host, port, username, filePath, clientIp);
            
            // 获取文件扩展名
            String extension = getFileExtension(filePath).toLowerCase();
            
            // 根据文件类型返回不同的预览内容
            Map<String, Object> previewData = new HashMap<>();
            previewData.put("fileName", getFileName(filePath));
            previewData.put("fileExtension", extension);
            previewData.put("filePath", filePath);
            
            if (isTextFile(extension)) {
                // 文本文件预览
                String content = sshFileService.getFileContent(host, port, username, password, filePath);
                previewData.put("type", "text");
                previewData.put("content", content);
                previewData.put("encoding", "UTF-8");
            } else if (isImageFile(extension)) {
                // 图片文件预览
                byte[] imageData = sshFileService.downloadFile(host, port, username, password, filePath);
                String base64Image = java.util.Base64.getEncoder().encodeToString(imageData);
                previewData.put("type", "image");
                previewData.put("content", "data:image/" + extension + ";base64," + base64Image);
                previewData.put("size", imageData.length);
            } else if (isPdfFile(extension)) {
                // PDF文件预览
                byte[] pdfData = sshFileService.downloadFile(host, port, username, password, filePath);
                String base64Pdf = java.util.Base64.getEncoder().encodeToString(pdfData);
                previewData.put("type", "pdf");
                previewData.put("content", "data:application/pdf;base64," + base64Pdf);
                previewData.put("size", pdfData.length);
            } else {
                // 不支持预览的文件类型
                previewData.put("type", "unsupported");
                previewData.put("message", "不支持预览此类型的文件: " + extension);
            }
            
            logAuditEvent("PREVIEW_FILE", host, port, username, "路径: " + filePath + ", 类型: " + extension, clientIp, true);
            logger.info("文件预览成功 - 主机: {}:{}, 路径: {}, 类型: {}, 客户端IP: {}", host, port, filePath, extension, clientIp);
            
            return ResponseEntity.ok(Map.of("success", true, "data", previewData, "message", "文件预览成功", "code", "SUCCESS"));
            
        } catch (Exception e) {
            logger.error("文件预览失败 - 客户端IP: {}, 错误: {}", clientIp, e.getMessage(), e);
            return ResponseEntity.ok(Map.of("success", false, "message", "文件预览失败: " + e.getMessage(), "code", "PREVIEW_FAILED"));
        }
    }
    
    /**
     * 测试SSH连接
     */
    @PostMapping("/connect")
    public ResponseEntity<?> testConnection(@RequestBody Map<String, Object> request, HttpServletRequest httpRequest) {
        String clientIp = getClientIpAddress(httpRequest);
        logger.info("测试SSH连接请求 - 客户端IP: {}", clientIp);
        
        try {
            String host = (String) request.get("host");
            Integer port = (Integer) request.get("port");
            String username = (String) request.get("username");
            String password = (String) request.get("password");
            
            if (host == null || port == null || username == null || password == null) {
                logger.warn("测试连接失败 - 缺少必要参数, 客户端IP: {}", clientIp);
                return ResponseEntity.badRequest().body(Map.of("success", false, "message", "缺少必要参数", "code", "MISSING_PARAMS"));
            }
            
            logger.info("开始测试SSH连接 - 主机: {}:{}, 用户: {}, 客户端IP: {}", host, port, username, clientIp);
            
            // 测试连接
            String connectionId = sshFileService.connect(host, port, username, password);
            
            logAuditEvent("TEST_CONNECTION", host, port, username, "连接ID: " + connectionId, clientIp, true);
            logger.info("测试SSH连接成功 - 主机: {}:{}, 用户: {}, 连接ID: {}, 客户端IP: {}", host, port, username, connectionId, clientIp);
            
            return ResponseEntity.ok(Map.of("success", true, "message", "连接成功", "connectionId", connectionId, "code", "SUCCESS"));
            
        } catch (Exception e) {
            String host = (String) request.get("host");
            Integer port = (Integer) request.get("port");
            String username = (String) request.get("username");
            
            logAuditEvent("TEST_CONNECTION", host != null ? host : "unknown", port != null ? port : 0, username != null ? username : "unknown", "错误: " + e.getMessage(), clientIp, false);
            logger.error("测试SSH连接失败 - 客户端IP: {}, 错误: {}", clientIp, e.getMessage(), e);
            return ResponseEntity.ok(Map.of("success", false, "message", "连接失败: " + e.getMessage(), "code", "CONNECTION_FAILED"));
        }
    }
    
    /**
     * 获取连接池状态
     */
    @GetMapping("/status")
    public ResponseEntity<?> getConnectionPoolStatus(HttpServletRequest httpRequest) {
        String clientIp = getClientIpAddress(httpRequest);
        logger.info("获取连接池状态请求 - 客户端IP: {}", clientIp);
        
        try {
            Map<String, Object> status = sshFileService.getConnectionPoolStatus();
            logger.info("获取连接池状态成功 - 客户端IP: {}", clientIp);
            return ResponseEntity.ok(Map.of("success", true, "data", status, "message", "获取连接池状态成功", "code", "SUCCESS"));
        } catch (Exception e) {
            logger.error("获取连接池状态失败 - 客户端IP: {}, 错误: {}", clientIp, e.getMessage(), e);
            return ResponseEntity.ok(Map.of("success", false, "message", "获取连接池状态失败: " + e.getMessage(), "code", "GET_POOL_STATUS_FAILED"));
        }
    }
    
    /**
     * 获取客户端真实IP地址
     */
    private String getClientIpAddress(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        String xRealIp = request.getHeader("X-Real-IP");
        String xForwardedProto = request.getHeader("X-Forwarded-Proto");
        String httpClientIp = request.getHeader("HTTP_CLIENT_IP");
        String httpXForwardedFor = request.getHeader("HTTP_X_FORWARDED_FOR");
        
        if (xForwardedFor != null && !xForwardedFor.isEmpty() && !"unknown".equalsIgnoreCase(xForwardedFor)) {
            // X-Forwarded-For可能包含多个IP，取第一个
            int index = xForwardedFor.indexOf(',');
            if (index != -1) {
                return xForwardedFor.substring(0, index).trim();
            } else {
                return xForwardedFor.trim();
            }
        }
        
        if (xRealIp != null && !xRealIp.isEmpty() && !"unknown".equalsIgnoreCase(xRealIp)) {
            return xRealIp;
        }
        
        if (httpClientIp != null && !httpClientIp.isEmpty() && !"unknown".equalsIgnoreCase(httpClientIp)) {
            return httpClientIp;
        }
        
        if (httpXForwardedFor != null && !httpXForwardedFor.isEmpty() && !"unknown".equalsIgnoreCase(httpXForwardedFor)) {
            return httpXForwardedFor;
        }
        
        return request.getRemoteAddr();
    }
    
    /**
     * 记录操作审计日志
     */
    private void logAuditEvent(String operation, String host, Integer port, String username, String details, String clientIp, boolean success) {
        if (success) {
            logger.info("审计日志 - 操作: {}, 主机: {}:{}, 用户: {}, 详情: {}, 客户端IP: {}, 结果: 成功", 
                       operation, host, port, username, details, clientIp);
        } else {
            logger.warn("审计日志 - 操作: {}, 主机: {}:{}, 用户: {}, 详情: {}, 客户端IP: {}, 结果: 失败", 
                       operation, host, port, username, details, clientIp);
        }
    }
    
    /**
     * 获取文件扩展名
     */
    private String getFileExtension(String filePath) {
        if (filePath == null || filePath.isEmpty()) {
            return "";
        }
        int lastDotIndex = filePath.lastIndexOf('.');
        if (lastDotIndex == -1 || lastDotIndex == filePath.length() - 1) {
            return "";
        }
        return filePath.substring(lastDotIndex + 1);
    }
    
    /**
     * 获取文件名
     */
    private String getFileName(String filePath) {
        if (filePath == null || filePath.isEmpty()) {
            return "";
        }
        int lastSlashIndex = Math.max(filePath.lastIndexOf('/'), filePath.lastIndexOf('\\'));
        if (lastSlashIndex == -1) {
            return filePath;
        }
        return filePath.substring(lastSlashIndex + 1);
    }
    
    /**
     * 判断是否为文本文件
     */
    private boolean isTextFile(String extension) {
        String[] textExtensions = {
            "txt", "log", "md", "json", "xml", "html", "htm", "css", "js", "ts",
            "java", "py", "cpp", "c", "h", "hpp", "php", "sql", "yml", "yaml",
            "properties", "conf", "cfg", "ini", "sh", "bat", "ps1", "dockerfile",
            "gitignore", "gitattributes", "readme", "license", "makefile", "gradle",
            "maven", "pom", "build", "config", "settings", "env", "csv", "tsv"
        };
        
        for (String textExt : textExtensions) {
            if (textExt.equalsIgnoreCase(extension)) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * 判断是否为图片文件
     */
    private boolean isImageFile(String extension) {
        String[] imageExtensions = {
            "jpg", "jpeg", "png", "gif", "bmp", "svg", "webp", "ico", "tiff", "tif"
        };
        
        for (String imageExt : imageExtensions) {
            if (imageExt.equalsIgnoreCase(extension)) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * 判断是否为PDF文件
     */
    private boolean isPdfFile(String extension) {
        return "pdf".equalsIgnoreCase(extension);
    }
}
package net.thekingofduck.loki.service;

import net.thekingofduck.loki.entity.SshServerEntity;
import net.thekingofduck.loki.mapper.SshServerMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.Base64Utils;

import javax.annotation.PostConstruct;
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

@Service
public class SshServerService {
    @Autowired
    private SshServerMapper sshServerMapper;
    
    // AES加密密钥
    private static final String AES_KEY = "LokiSSHServerKey1234567890AB1234"; // 32字节密钥
    
    @PostConstruct
    public void init() {
        // 初始化表
        sshServerMapper.createTable();
    }
    
    /**
     * 检查是否存在相同的SSH服务器（基于host, port, username）
     */
    public SshServerEntity getExistingServer(SshServerEntity server) {
        List<SshServerEntity> existingServers = sshServerMapper.getAllServers();
        for (SshServerEntity existingServer : existingServers) {
            if (existingServer.getHost().equals(server.getHost()) &&
                existingServer.getPort() == server.getPort() &&
                existingServer.getUsername().equals(server.getUsername())) {
                return existingServer;
            }
        }
        return null;
    }
    
    /**
     * 添加SSH服务器
     */
    public Integer addServer(SshServerEntity server) {
        // 检查是否已存在相同的SSH服务器（基于host, port, username）
        if (getExistingServer(server) != null) {
            // 如果已存在，返回0表示添加失败
            return 0;
        }
        
        // 设置创建时间
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        server.setCreateTime(sdf.format(new Date()));
        // 密码加密存储
        try {
            String encryptedPassword = encrypt(server.getPassword());
            server.setPassword(encryptedPassword);
        } catch (Exception e) {
            // 如果加密失败，记录日志并使用原始密码
            e.printStackTrace();
        }
        
        return sshServerMapper.addServer(server);
    }
    
    /**
     * 获取所有SSH服务器
     */
    public List<SshServerEntity> getAllServers() {
        List<SshServerEntity> servers = sshServerMapper.getAllServers();
        // 解密密码
        for (SshServerEntity server : servers) {
            try {
                // 判断密码是否已加密（简单判断是否为Base64格式）
                if (isBase64(server.getPassword())) {
                    String decryptedPassword = decrypt(server.getPassword());
                    server.setPassword(decryptedPassword);
                }
                // 如果不是Base64格式，说明是明文密码或无效数据，保持原样
            } catch (Exception e) {
                // 如果解密失败，可能是使用了旧的加密方式或者数据损坏，保持加密状态的密码
                System.err.println("解密密码失败，保持原始加密数据: " + e.getMessage());
            }
        }
        return servers;
    }
    
    /**
     * 删除SSH服务器
     */
    public Integer deleteServerById(int id) {
        return sshServerMapper.deleteServerById(id);
    }
    
    /**
     * 更新SSH服务器
     */
    public Integer updateServer(SshServerEntity server) {
        // 密码加密存储
        try {
            String encryptedPassword = encrypt(server.getPassword());
            server.setPassword(encryptedPassword);
        } catch (Exception e) {
            // 如果加密失败，记录日志并使用原始密码
            e.printStackTrace();
        }
        return sshServerMapper.updateServer(server);
    }
    
    /**
     * AES加密
     */
    private String encrypt(String content) throws Exception {
        SecretKeySpec keySpec = new SecretKeySpec(AES_KEY.getBytes(), "AES");
        Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
        cipher.init(Cipher.ENCRYPT_MODE, keySpec);
        byte[] encrypted = cipher.doFinal(content.getBytes());
        return Base64Utils.encodeToString(encrypted);
    }
    
    /**
     * AES解密
     */
    private String decrypt(String content) throws Exception {
        SecretKeySpec keySpec = new SecretKeySpec(AES_KEY.getBytes(), "AES");
        Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
        cipher.init(Cipher.DECRYPT_MODE, keySpec);
        byte[] decrypted = cipher.doFinal(Base64Utils.decodeFromString(content));
        return new String(decrypted);
    }
    
    /**
     * 判断字符串是否为Base64编码
     */
    private boolean isBase64(String str) {
        try {
            Base64Utils.decodeFromString(str);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
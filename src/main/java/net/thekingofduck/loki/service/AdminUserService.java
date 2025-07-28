package net.thekingofduck.loki.service;

import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import net.thekingofduck.loki.entity.AdminUser;
import net.thekingofduck.loki.mapper.AdminUserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import cn.hutool.crypto.digest.DigestAlgorithm;
import cn.hutool.crypto.digest.Digester;
import cn.hutool.core.util.RandomUtil;

@Service
public class AdminUserService {
    static Log log = LogFactory.get(Thread.currentThread().getStackTrace()[1].getClassName());
    @Autowired
    private AdminUserMapper adminUserMapper;
    
    /**
     * 验证管理员用户登录
     * @param username 用户名
     * @param password 密码（明文）
     * @return 验证是否成功
     */
    public boolean validateAdminUser(String username, String password) {
        AdminUser adminUser = adminUserMapper.getAdminUserByUsername(username);
        if (adminUser == null) {
            return false;
        }
        
        // 使用存储的盐值和提供的密码生成哈希值
        String hashedPassword = hashPassword(password, adminUser.getSalt());
        log.info(String.format("LoginData22233: %s %s", hashedPassword,adminUser.getPassword()));
        // 比较生成的哈希值与存储的哈希值
        return hashedPassword.equals(adminUser.getPassword());
    }
    
    /**
     * 创建新的管理员用户
     * @param username 用户名
     * @param password 密码（明文）
     * @param role 角色
     * @return 是否创建成功
     */
    public boolean createAdminUser(String username, String password, String role) {
        AdminUser adminUser = new AdminUser();
        adminUser.setUsername(username);
        adminUser.setRole(role);
        adminUser.setCreatedTime(java.time.LocalDateTime.now().toString());
        
        // 生成盐值
        String salt = RandomUtil.randomString(16);
        adminUser.setSalt(salt);
        
        // 使用盐值对密码进行哈希
        String hashedPassword = hashPassword(password, salt);
        adminUser.setPassword(hashedPassword);
        
        try {
            return adminUserMapper.insertAdminUser(adminUser) > 0;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
    
    /**
     * 更新管理员用户密码
     * @param username 用户名
     * @param newPassword 新密码（明文）
     * @return 是否更新成功
     */
    public boolean updateAdminUserPassword(String username, String newPassword) {
        AdminUser adminUser = adminUserMapper.getAdminUserByUsername(username);
        if (adminUser == null) {
            return false;
        }
        
        // 生成新的盐值
        String newSalt = RandomUtil.randomString(16);
        adminUser.setSalt(newSalt);
        
        // 使用新盐值对新密码进行哈希
        String hashedPassword = hashPassword(newPassword, newSalt);
        adminUser.setPassword(hashedPassword);
        
        try {
            return adminUserMapper.updateAdminUserPassword(adminUser) > 0;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
    
    /**
     * 使用盐值对密码进行哈希
     * @param password 明文密码
     * @param salt 盐值
     * @return 哈希后的密码
     */
    private String hashPassword(String password, String salt) {
        Digester sha256 = new Digester(DigestAlgorithm.SHA256);
        return sha256.digestHex(password + salt);
    }




}
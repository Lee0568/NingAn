package net.thekingofduck.loki.interceptor;

import net.thekingofduck.loki.config.SecurityPolicyManager;
import net.thekingofduck.loki.model.BlockedIp;
import net.thekingofduck.loki.repository.BlockedIpRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Deque;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

@Component
public class IpThrottlingInterceptor implements HandlerInterceptor {

    private static final Logger logger = LoggerFactory.getLogger(IpThrottlingInterceptor.class);
    private final Map<String, Deque<Long>> requestTimestamps = new ConcurrentHashMap<>();

    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Autowired
    private BlockedIpRepository blockedIpRepository;
    @Autowired
    private SecurityPolicyManager policyManager;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String ip = getClientIp(request);

        Optional<BlockedIp> blockedIpOptional = blockedIpRepository.findById(ip);
        if (blockedIpOptional.isPresent()) {
            BlockedIp blockedIp = blockedIpOptional.get();

            try {
                // 将 String 类型的过期时间解析为 LocalDateTime 对象
                LocalDateTime expiresAt = LocalDateTime.parse(blockedIp.getExpiresAt(), formatter);

                // 再进行时间比较
                if (LocalDateTime.now().isBefore(expiresAt)) {
                    logger.warn("Blocked IP {} denied access.", ip);
                    response.setStatus(HttpStatus.FORBIDDEN.value());
                    response.getWriter().write("Your IP is currently blocked.");
                    return false;
                } else {
                    blockedIpRepository.delete(blockedIp);
                }
            } catch (Exception e) {
                logger.error("Failed to parse expires_at string: {}. Deleting record for IP: {}", blockedIp.getExpiresAt(), ip);
                blockedIpRepository.delete(blockedIp);
            }
        }

        long currentTime = System.currentTimeMillis();
        requestTimestamps.putIfAbsent(ip, new ConcurrentLinkedDeque<>());
        Deque<Long> timestamps = requestTimestamps.get(ip);
        while (!timestamps.isEmpty() && currentTime - timestamps.peekFirst() > 60000) {
            timestamps.pollFirst();
        }
        timestamps.addLast(currentTime);

        if (timestamps.size() > policyManager.getFrequencyThreshold()) {
            logger.warn("IP {} exceeded rate limit. Blocking now.", ip);

            BlockedIp newBlock = new BlockedIp();
            newBlock.setIpAddress(ip);

            // 计算 LocalDateTime 类型的过期时间
            LocalDateTime expiresAtLdt = LocalDateTime.now().plusMinutes(policyManager.getBanDurationMinutes());
            // 将 LocalDateTime 对象格式化为 String 类型
            String expiresAtStr = expiresAtLdt.format(formatter);
            // 将 String 类型的时间存入实体
            newBlock.setExpiresAt(expiresAtStr);

            newBlock.setBlockMode("自动");

            blockedIpRepository.save(newBlock);
            requestTimestamps.remove(ip);

            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.getWriter().write("Rate limit exceeded. Your IP has been blocked.");
            return false;
        }

        return true;
    }

    private String getClientIp(HttpServletRequest request) {
        String remoteAddr = request.getHeader("X-FORWARDED-FOR");
        if (remoteAddr == null || remoteAddr.isEmpty()) {
            remoteAddr = request.getRemoteAddr();
        }
        return remoteAddr;
    }
}
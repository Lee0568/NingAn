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
import java.util.Deque;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

@Component
public class IpThrottlingInterceptor implements HandlerInterceptor {

    private static final Logger logger = LoggerFactory.getLogger(IpThrottlingInterceptor.class);
    private final Map<String, Deque<Long>> requestTimestamps = new ConcurrentHashMap<>();

    @Autowired
    private BlockedIpRepository blockedIpRepository;
    @Autowired
    private SecurityPolicyManager policyManager;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String ip = getClientIp(request);

        // 步骤 1: 检查IP是否在黑名单中
        Optional<BlockedIp> blockedIpOptional = blockedIpRepository.findById(ip);
        if (blockedIpOptional.isPresent()) {
            BlockedIp blockedIp = blockedIpOptional.get();
            if (LocalDateTime.now().isBefore(blockedIp.getExpiresAt())) {
                logger.warn("Blocked IP {} denied access.", ip);
                response.setStatus(HttpStatus.FORBIDDEN.value());
                response.getWriter().write("Your IP is currently blocked.");
                return false;
            } else {
                blockedIpRepository.delete(blockedIp);
            }
        }

        // 步骤 2: 滑动窗口计数
        long currentTime = System.currentTimeMillis();
        requestTimestamps.putIfAbsent(ip, new ConcurrentLinkedDeque<>());
        Deque<Long> timestamps = requestTimestamps.get(ip);
        while (!timestamps.isEmpty() && currentTime - timestamps.peekFirst() > 60000) {
            timestamps.pollFirst();
        }
        timestamps.addLast(currentTime);

        // 步骤 3: 检查是否超限并执行封禁
        if (timestamps.size() > policyManager.getFrequencyThreshold()) {
            logger.warn("IP {} exceeded rate limit. Blocking now.", ip);

            BlockedIp newBlock = new BlockedIp();
            newBlock.setIpAddress(ip);
            LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(policyManager.getBanDurationMinutes());
            newBlock.setExpiresAt(expiresAt);

            // --- 关键更新 ---
            // 设置封禁模式为“自动”
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

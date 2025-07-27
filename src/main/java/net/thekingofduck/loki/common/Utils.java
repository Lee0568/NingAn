package net.thekingofduck.loki.common;

import cn.hutool.core.codec.Base64;
import org.springframework.util.StreamUtils;

import javax.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.util.Map;


public class Utils {
    public static String params2string(Map<String, String[]> parms) {

        String result ="";
        for(String key : parms.keySet()){
            String[] values = parms.get(key);
            String value ="";
            for (String str:values){
                value += str;
            }

            String param = String.format("%s=%s",key,value);
            result += String.format("%s&",param);
        }

        if (result.endsWith("&")){
            return result.substring(0,result.length() - 1);
        }

        return result;
    }

    public static String httpServletToBase64(HttpServletRequest request) {
        try {

            System.out.println(StreamUtils.copyToString(request.getInputStream(), StandardCharsets.UTF_8));
            String httpServlet = StreamUtils.copyToString(request.getInputStream(), StandardCharsets.UTF_8);
            System.out.println(Base64.encode(httpServlet));
            return Base64.encode(httpServlet);
        }catch (Exception ex){
            return Base64.encode(ex.getMessage());
        }
    }

    // 示例：IpUtils.java
    public class IpUtils {
        public static String getClientIp(HttpServletRequest request) {
            String ipAddress = request.getHeader("X-Forwarded-For");
            if (ipAddress == null || ipAddress.isEmpty() || "unknown".equalsIgnoreCase(ipAddress)) {
                ipAddress = request.getHeader("Proxy-Client-IP");
            }
            if (ipAddress == null || ipAddress.isEmpty() || "unknown".equalsIgnoreCase(ipAddress)) {
                ipAddress = request.getHeader("WL-Proxy-Client-IP");
            }
            if (ipAddress == null || ipAddress.isEmpty() || "unknown".equalsIgnoreCase(ipAddress)) {
                ipAddress = request.getRemoteAddr();
            }
            // 对于通过多个代理的情况，X-Forwarded-For可能会有多个IP，取第一个
            if (ipAddress != null && ipAddress.contains(",")) {
                ipAddress = ipAddress.split(",")[0].trim();
            }
            return ipAddress;
        }
    }

}

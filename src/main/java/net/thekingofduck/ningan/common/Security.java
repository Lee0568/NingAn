package net.thekingofduck.ningan.common;

import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;

import javax.servlet.http.HttpServletRequest;
import java.net.URLDecoder;

/**
 * Project: ningan
 * Date:2021/1/9 下午4:41
 * @author CoolCat
 * @version 1.0.0
 * Github:https://github.com/TheKingOfDuck
 * When I wirting my code, only God and I know what it does. After a while, only God knows.
 */
public class Security {
    static Log log = LogFactory.get(Thread.currentThread().getStackTrace()[1].getClassName());

    public static boolean check(HttpServletRequest request) {
        try {
            String url = request.getRequestURI();
            String decodedUrl = URLDecoder.decode(url, "UTF-8");
            if (url.contains("..") || decodedUrl.contains("..")) {
                log.warn("检测到目录遍历尝试: " + url);
                return false;
            }
            return true;
        } catch (Exception e) {
            log.error("安全检查异常", e);
            return false;
        }
    }
}

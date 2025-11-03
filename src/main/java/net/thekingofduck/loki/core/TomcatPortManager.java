package net.thekingofduck.loki.core;

import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import org.apache.catalina.core.StandardService;
import org.apache.catalina.connector.Connector;
import org.apache.coyote.http11.Http11NioProtocol;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.embedded.tomcat.TomcatWebServer;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 运行时管理 Tomcat 端口（Connector）的组件：支持新增/移除端口监听。
 */
@Component
public class TomcatPortManager {

    static Log log = LogFactory.get(Thread.currentThread().getStackTrace()[1].getClassName());

    @Autowired
    private ServletWebServerApplicationContext appContext;

    // 仅记录动态添加的 Connector，避免误操作启动时的静态端口
    private final Map<Integer, Connector> dynamicConnectors = new ConcurrentHashMap<>();

    private StandardService getService() {
        TomcatWebServer webServer = (TomcatWebServer) appContext.getWebServer();
        return (StandardService) webServer.getTomcat().getService();
    }

    public synchronized boolean addPort(int port, boolean ssl) {
        try {
            if (dynamicConnectors.containsKey(port)) {
                log.info("Port " + port + " already added dynamically");
                return true;
            }
            StandardService service = getService();

            Connector connector = new Connector(Http11NioProtocol.class.getName());
            connector.setPort(port);
            connector.setScheme(ssl ? "https" : "http");
            connector.setSecure(ssl);

            service.addConnector(connector);
            connector.start();
            dynamicConnectors.put(port, connector);
            log.info("Added dynamic port: " + port);
            return true;
        } catch (Exception e) {
            log.error(e, "Failed to add dynamic port: " + port);
            return false;
        }
    }

    public synchronized boolean removePort(int port) {
        Connector connector = dynamicConnectors.remove(port);
        if (connector == null) {
            log.info("No dynamic connector found for port: " + port);
            return false;
        }
        try {
            StandardService service = getService();
            service.removeConnector(connector);
            try { connector.stop(); } catch (Exception ignored) {}
            log.info("Removed dynamic port: " + port);
            return true;
        } catch (Exception e) {
            log.error(e, "Failed to remove dynamic port: " + port);
            return false;
        }
    }
}
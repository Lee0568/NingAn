package net.thekingofduck.ningan.core;

import com.alibaba.fastjson.JSON;
import com.jcraft.jsch.*;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 终端WebSocket处理器
 */
@Component
public class WebSSHHandler extends TextWebSocketHandler {

    public static class ConnectInfo {
        private String host;
        private int port;
        private String username;
        private String password;

        public String getHost() { return host; }
        public void setHost(String host) { this.host = host; }
        public int getPort() { return port; }
        public void setPort(int port) { this.port = port; }
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
    }

    private final Map<String, Session> sshSessions = new ConcurrentHashMap<>();

    private static class Session {
        private com.jcraft.jsch.Session sshSession;
        private WebSocketSession ws;
        private ChannelShell channel;

        Session(WebSocketSession ws) {
            this.ws = ws;
        }

        void connect(ConnectInfo info) throws JSchException, IOException {
            JSch jSch = new JSch();
            sshSession = jSch.getSession(info.getUsername(), info.getHost(), info.getPort());
            sshSession.setPassword(info.getPassword());
            sshSession.setConfig("StrictHostKeyChecking", "no");
            // 保活设置，提升连接稳定性
            sshSession.setServerAliveInterval(30_000);
            sshSession.setServerAliveCountMax(3);
            sshSession.connect(5000);

            channel = (ChannelShell) sshSession.openChannel("shell");
            channel.setPtyType("xterm");

            // 不设置OutputStream，让SSH输出通过InputStream读取

            channel.connect();

            new Thread(() -> {
                try (InputStream in = channel.getInputStream()) {
                    byte[] buf = new byte[1024];
                    int len;
                    while ((len = in.read(buf)) != -1) {
                        ws.sendMessage(new TextMessage(new String(buf, 0, len)));
                    }
                } catch (Exception ignored) {
                }
            }).start();
        }

        void transToSSH(String cmd) throws IOException {
            if (channel != null && channel.isConnected()) {
                // 直接发送命令，不做任何修改
                // 前端已经处理了回车键为\r，这里直接传输
                OutputStream out = channel.getOutputStream();
                out.write(cmd.getBytes(StandardCharsets.UTF_8));
                out.flush();
            } else {
                throw new IOException("SSH channel is not connected");
            }
        }

        void close() {
            if (channel != null) {
                channel.disconnect();
            }
            if (sshSession != null) {
                sshSession.disconnect();
            }
        }
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sshSessions.put(session.getId(), new Session(session));
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        Session s = sshSessions.get(session.getId());
        if (s == null) {
            return;
        }

        if (s.channel == null) {
            ConnectInfo info = JSON.parseObject(message.getPayload(), ConnectInfo.class);
            try {
                s.connect(info);
            } catch (JSchException | IOException e) {
                session.sendMessage(new TextMessage("SSH连接失败: " + e.getMessage()));
                session.close();
                sshSessions.remove(session.getId());
            }
        } else {
            // 尝试识别前端的控制消息（例如调整终端大小）
            String payload = message.getPayload();
            boolean handledControl = false;
            try {
                // 简单判断是否为JSON对象
                if (payload != null && payload.startsWith("{")) {
                    Map<?,?> obj = JSON.parseObject(payload, Map.class);
                    Object type = obj.get("type");
                    if (type != null && "resize".equals(type.toString())) {
                        // 终端尺寸调整
                        Integer cols = safeInt(obj.get("cols"), 80);
                        Integer rows = safeInt(obj.get("rows"), 24);
                        if (s.channel != null && s.channel.isConnected()) {
                            // 像素值可不填，由JSch估算，这里传0
                            s.channel.setPtySize(cols, rows, 0, 0);
                        }
                        handledControl = true;
                    }
                }
            } catch (Exception ignored) {
                // 不是控制JSON，按普通输入处理
            }

            if (!handledControl) {
                try {
                    s.transToSSH(payload);
                } catch (IOException e) {
                    session.sendMessage(new TextMessage("命令发送失败: " + e.getMessage()));
                    session.close();
                    sshSessions.remove(session.getId());
                }
            }
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Optional.ofNullable(sshSessions.remove(session.getId()))
                .ifPresent(Session::close);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        Optional.ofNullable(sshSessions.remove(session.getId()))
                .ifPresent(Session::close);
    }

    private Integer safeInt(Object v, int def) {
        try {
            if (v == null) return def;
            if (v instanceof Number) return ((Number) v).intValue();
            return Integer.parseInt(v.toString());
        } catch (Exception e) {
            return def;
        }
    }
}

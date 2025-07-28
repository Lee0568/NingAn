package net.thekingofduck.loki.core;

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
            sshSession.connect(5000);

            channel = (ChannelShell) sshSession.openChannel("shell");
            channel.setPtyType("xterm");

            channel.setOutputStream(new OutputStream() {
                @Override
                public void write(int b) {
                }

                @Override
                public void write(byte[] b, int off, int len) throws IOException {
                    ws.sendMessage(new TextMessage(Arrays.copyOfRange(b, off, off + len)));
                }
            });

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
                channel.getOutputStream().write(cmd.getBytes(StandardCharsets.UTF_8));
                channel.getOutputStream().flush();
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
            try {
                s.transToSSH(message.getPayload());
            } catch (IOException e) {
                session.sendMessage(new TextMessage("命令发送失败: " + e.getMessage()));
                session.close();
                sshSessions.remove(session.getId());
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
}

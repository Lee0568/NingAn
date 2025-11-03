package net.thekingofduck.loki.protocol.ssh;

import org.apache.sshd.server.SshServer;
import org.apache.sshd.server.auth.password.AcceptAllPasswordAuthenticator;
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider;
import org.apache.sshd.server.session.ServerSession;
import org.apache.sshd.common.session.Session;
import org.apache.sshd.common.session.SessionListener;
import org.apache.sshd.server.command.Command;
import org.apache.sshd.server.Environment;
import org.apache.sshd.server.shell.ShellFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicBoolean;
import net.thekingofduck.loki.service.SshAuditService;

/**
 * 轻量 SSH 协议蜜罐：基于 Apache Mina SSHD
 * - 接受任意用户名密码（可替换为固定凭据）
 * - 提供伪 Shell，不执行系统命令，所有输入被记录并返回模拟输出
 */
public class SshHoneypotServer {
    private static final Logger log = LoggerFactory.getLogger(SshHoneypotServer.class);

    private final int port;
    private final String banner;
    private final Path hostKeyPath;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private SshServer server;
    private SshAuditService auditService;

    public SshHoneypotServer(int port) {
        this(port, "Welcome to Loki SSH Honeypot\r\n");
    }

    public SshHoneypotServer(int port, String banner) {
        this.port = port;
        this.banner = banner;
        this.hostKeyPath = Paths.get("data", "ssh-honeypot-hostkey.ser");
    }

    public void setAuditService(SshAuditService auditService){
        this.auditService = auditService;
    }

    public synchronized void start() throws IOException {
        if (running.get()) return;
        server = SshServer.setUpDefaultServer();
        server.setPort(port);
        // 生成或加载主机密钥
        ensureHostKey();
        server.setKeyPairProvider(new SimpleGeneratorHostKeyProvider(hostKeyPath));
        // 接受所有用户名密码（蜜罐场景记录用）
        server.setPasswordAuthenticator(AcceptAllPasswordAuthenticator.INSTANCE);
        // 简单 ShellFactory，提供伪命令处理
        server.setShellFactory(new FakeShellFactory(auditService, port));
        // 连接事件日志
        server.addSessionListener(new SessionListener() {
            @Override
            public void sessionCreated(Session session) {
                if (session instanceof ServerSession) {
                    ServerSession s = (ServerSession) session;
                    log.info("SSH会话创建: {}@{}:{}", s.getUsername(), s.getClientAddress(), port);
                    if (auditService != null) {
                        String remote = String.valueOf(s.getClientAddress());
                        auditService.logConnect(remote, s.getUsername(), port);
                    }
                } else {
                    log.info("SSH会话创建: {}", session);
                }
            }
            @Override
            public void sessionEvent(Session session, SessionListener.Event event) {
                // no-op
            }
            @Override
            public void sessionClosed(Session session) {
                if (session instanceof ServerSession) {
                    ServerSession s = (ServerSession) session;
                    log.info("SSH会话关闭: {}@{}:{}", s.getUsername(), s.getClientAddress(), port);
                    if (auditService != null) {
                        String remote = String.valueOf(s.getClientAddress());
                        auditService.logDisconnect(remote, s.getUsername(), port);
                    }
                } else {
                    log.info("SSH会话关闭: {}", session);
                }
            }
        });

        try {
            server.start();
            running.set(true);
            log.info("SSH蜜罐已启动，端口:{}", port);
        } catch (IOException e) {
            log.error("SSH蜜罐启动失败，端口:{}", port, e);
            throw e;
        }
    }

    public synchronized void stop() {
        if (!running.get()) return;
        try {
            server.stop(true);
        } catch (IOException e) {
            log.warn("SSH蜜罐停止异常，端口:{}", port, e);
        } finally {
            running.set(false);
            log.info("SSH蜜罐已停止，端口:{}", port);
        }
    }

    public boolean isRunning() { return running.get(); }

    private void ensureHostKey() throws IOException {
        if (!Files.exists(hostKeyPath)) {
            Files.createDirectories(hostKeyPath.getParent());
            // SimpleGeneratorHostKeyProvider 会自动生成；这里确保父目录存在
        }
    }

    /**
     * 伪Shell实现：读取客户端输入，输出提示与模拟命令结果，并记录日志
     */
    static class FakeShellFactory implements ShellFactory {
        private final SshAuditService auditService;
        private final int port;

        FakeShellFactory(SshAuditService auditService, int port){
            this.auditService = auditService;
            this.port = port;
        }
        @Override
        public Command createShell(org.apache.sshd.server.channel.ChannelSession channel) {
            return new FakeCommand(auditService, port);
        }

        static class FakeCommand implements Command, Runnable {
            private OutputStream out;
            private OutputStream err;
            private InputStream in;
            private org.apache.sshd.server.channel.ChannelSession channel;
            private final AtomicBoolean closed = new AtomicBoolean(false);
            private final SshAuditService auditService;
            private final int port;

            FakeCommand(SshAuditService auditService, int port){
                this.auditService = auditService;
                this.port = port;
            }

            @Override
            public void setInputStream(InputStream in) { this.in = in; }

            @Override
            public void setOutputStream(OutputStream out) { this.out = out; }

            @Override
            public void setErrorStream(OutputStream err) { this.err = err; }

            @Override
            public void setExitCallback(org.apache.sshd.server.ExitCallback callback) { /* no-op */ }

            @Override
            public void start(org.apache.sshd.server.channel.ChannelSession channel, Environment env) throws IOException {
                this.channel = channel;
                write(out, "" +
                        "\r\n" +
                        "" + DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").format(LocalDateTime.now()) + "\r\n" +
                        "" + "Loki SSH Honeypot (simulated)" + "\r\n" +
                        "" + "Type 'help' for supported commands." + "\r\n" +
                        "[admin@server ~]$ ");
                new Thread(this, "fake-shell-thread").start();
            }

            @Override
            public void destroy(org.apache.sshd.server.channel.ChannelSession channel) throws Exception {
                closed.set(true);
            }

            @Override
            public void run() {
                byte[] buf = new byte[1024];
                try {
                    while (!closed.get()) {
                        int len = in.read(buf);
                        if (len <= 0) break;
                        String s = new String(buf, 0, len);
                        // 处理输入可能包含退格、控制字符，仅取行
                        for (String line : s.replace("\r", "\n").split("\n")) {
                            String cmd = line.trim();
                            if (cmd.isEmpty()) continue;
                            log.info("SSH命令: {}", cmd);
                            String resp = handleCommand(cmd);
                            if (auditService != null && channel != null) {
                                String username = channel.getServerSession().getUsername();
                                String remote = String.valueOf(channel.getServerSession().getClientAddress());
                                auditService.logCommand(remote, username, port, cmd);
                            }
                            write(out, resp + "\r\n" + "[admin@server ~]$ ");
                        }
                    }
                } catch (IOException ignored) {
                } finally {
                    try { out.flush(); } catch (Exception ignored) {}
                }
            }

            private String handleCommand(String cmd) {
                switch (cmd) {
                    case "help":
                        return "Available: whoami, hostname, uname -a, ls, pwd, id, cat, exit";
                    case "whoami":
                        return "admin";
                    case "hostname":
                        return "server";
                    case "uname -a":
                        return "Linux server 5.10.0-23-amd64 #1 SMP Debian x86_64";
                    case "pwd":
                        return "/home/admin";
                    case "ls":
                        return "README.md  app  conf  logs  data";
                    case "id":
                        return "uid=1000(admin) gid=1000(admin) groups=1000(admin)";
                    case "exit":
                        closed.set(true);
                        return "bye";
                    default:
                        // 简单模拟 cat /etc/passwd 等常见尝试
                        if (cmd.startsWith("cat ")) {
                            String path = cmd.substring(4).trim();
                            if ("/etc/passwd".equals(path)) {
                                return "root:x:0:0:root:/root:/bin/bash\nadmin:x:1000:1000:admin:/home/admin:/bin/bash";
                            }
                            return "cat: " + path + ": Permission denied";
                        }
                        return "bash: " + cmd + ": command not found";
                }
            }

            private void write(OutputStream out, String s) throws IOException {
                out.write(s.getBytes());
                out.flush();
            }
        }
    }
}
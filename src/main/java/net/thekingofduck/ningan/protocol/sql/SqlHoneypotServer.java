package net.thekingofduck.ningan.protocol.sql;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import net.thekingofduck.ningan.service.SqlAuditService;

/**
 * 极简 MySQL 协议蜜罐：握手 -> 接受认证 -> 处理 COM_QUERY
 * 支持基础查询：SELECT version(), SELECT user(), SHOW DATABASES
 * 其余查询返回错误包。仅用于蜜罐交互记录与诱捕，不做真实数据库操作。
 */
public class SqlHoneypotServer {
    private static final Logger log = LoggerFactory.getLogger(SqlHoneypotServer.class);

    private final int port;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private ServerSocket serverSocket;
    private ExecutorService pool;
    private SqlAuditService auditService;

    public SqlHoneypotServer(int port) {
        this.port = port;
    }

    public void setAuditService(SqlAuditService auditService){
        this.auditService = auditService;
    }

    public synchronized void start() throws IOException {
        if (running.get()) return;
        serverSocket = new ServerSocket(port);
        pool = Executors.newCachedThreadPool();
        running.set(true);
        pool.submit(() -> {
            log.info("SQL蜜罐监听端口:{}", port);
            while (running.get()) {
                try {
                    Socket s = serverSocket.accept();
                    pool.submit(new Handler(s, auditService));
                } catch (IOException e) {
                    if (running.get()) log.warn("SQL蜜罐accept异常", e);
                }
            }
        });
    }

    public synchronized void stop() {
        if (!running.get()) return;
        running.set(false);
        try { serverSocket.close(); } catch (IOException ignored) {}
        if (pool != null) pool.shutdownNow();
        log.info("SQL蜜罐已停止，端口:{}", port);
    }

    public boolean isRunning() { return running.get(); }

    static class Handler implements Runnable {
        private final Socket socket;
        private final SqlAuditService auditService;
        private final Random rnd = new Random();
        private String username = "";

        Handler(Socket s, SqlAuditService auditService) {
            this.socket = s;
            this.auditService = auditService;
        }

        @Override
        public void run() {
            try (Socket s = socket; InputStream in = s.getInputStream(); OutputStream out = s.getOutputStream()) {
                log.info("SQL连接: {}", s.getRemoteSocketAddress());
                int seq = 0;
                // 1) 发送握手
                byte[] hs = handshakePacket();
                writePacket(out, seq++, hs);
                // 2) 读取认证响应
                byte[] auth = readPacket(in);
                if (auth == null) return;
                // 尝试解析用户名（协议41：flags[4] maxpkt[4] charset[1] username[NUL] ...）
                try {
                    int off = 4 + 4 + 1; // skip flags/maxpkt/charset
                    int end = off;
                    while (end < auth.length && auth[end] != 0) end++;
                    if (end > off) {
                        username = new String(auth, off, end - off, StandardCharsets.UTF_8);
                    }
                } catch (Exception ignored) {}
                // 3) 返回OK包（接受任意用户名密码）
                writePacket(out, seq++, okPacket());
                // 4) 循环读取命令
                while (true) {
                    byte[] p = readPacket(in);
                    if (p == null || p.length == 0) break;
                    int cmd = p[0] & 0xFF;
                    if (cmd == 0x01) { // COM_QUIT
                        break;
                    } else if (cmd == 0x03) { // COM_QUERY
                        String sql = new String(p, 1, p.length - 1, StandardCharsets.UTF_8).trim();
                        log.info("SQL查询: {}", sql.replace('\n',' '));
                        if (auditService != null) {
                            String remote = String.valueOf(socket.getRemoteSocketAddress());
                            auditService.logQuery(remote, username, socket.getLocalPort(), sql);
                        }
                        seq = 0; // 每个结果集从序号0开始
                        if (equalsIgnoreCaseNoSpace(sql, "select version()") || equalsIgnoreCaseNoSpace(sql, "select @@version") ) {
                            // 发起单列版本号结果集
                            byte[][] rs = singleColumnResult("version()", "5.7.31-ningan");
                            for (byte[] pkt : rs) writePacket(out, seq++, pkt);
                        } else if (equalsIgnoreCaseNoSpace(sql, "select user()") || equalsIgnoreCaseNoSpace(sql, "select current_user()")) {
                            byte[][] rs = singleColumnResult("user()", "admin@localhost");
                            for (byte[] pkt : rs) writePacket(out, seq++, pkt);
                        } else if (sql.equalsIgnoreCase("show databases") || sql.equalsIgnoreCase("SHOW DATABASES;")) {
                            byte[][] rs = resultSet(new String[]{"Database"}, new String[][]{{"information_schema"},{"app"},{"logs"}});
                            for (byte[] pkt : rs) writePacket(out, seq++, pkt);
                        } else {
                            writePacket(out, seq++, errorPacket(1064, "You have an error in your SQL syntax"));
                        }
                    } else {
                        // 其他命令：直接返回错误
                        writePacket(out, 0, errorPacket(0xFFFF & rnd.nextInt(4000), "Command not supported"));
                    }
                }
            } catch (IOException ignored) {
            }
        }

        private boolean equalsIgnoreCaseNoSpace(String a, String b) {
            return a.replaceAll("\\s+", "").equalsIgnoreCase(b.replaceAll("\\s+", ""));
        }

        // --- Packet helpers ---
        private static void writePacket(OutputStream out, int seq, byte[] payload) throws IOException {
            int len = payload.length;
            byte[] header = new byte[]{(byte)(len & 0xFF), (byte)((len >> 8) & 0xFF), (byte)((len >> 16) & 0xFF), (byte)(seq & 0xFF)};
            out.write(header);
            out.write(payload);
            out.flush();
        }

        private static byte[] readPacket(InputStream in) throws IOException {
            byte[] hdr = new byte[4];
            int r = readFully(in, hdr, 0, 4);
            if (r < 4) return null;
            int len = (hdr[0] & 0xFF) | ((hdr[1] & 0xFF) << 8) | ((hdr[2] & 0xFF) << 16);
            byte[] payload = new byte[len];
            r = readFully(in, payload, 0, len);
            if (r < len) return null;
            return payload;
        }

        private static int readFully(InputStream in, byte[] b, int off, int len) throws IOException {
            int read = 0;
            while (read < len) {
                int r = in.read(b, off + read, len - read);
                if (r < 0) break;
                read += r;
            }
            return read;
        }

        private byte[] handshakePacket() {
            // Protocol 10 handshake
            byte[] serverVersion = strz("5.7.31-ningan");
            byte[] auth1 = randomBytes(8);
            byte[] filler = new byte[]{0x00};
            int capLower = 0xFFFF & (0x0001 | 0x0008 | 0x0020 | 0x0200); // CLIENT_LONG_PASSWORD | CLIENT_LONG_FLAG | CLIENT_TRANSACTIONS | CLIENT_PROTOCOL_41
            int capUpper = 0x80000 | 0x00008000; // CLIENT_PLUGIN_AUTH | CLIENT_SECURE_CONNECTION
            byte charset = 0x21; // utf8_general_ci
            int status = 0x0002; // autocommit
            byte authLen = 21;
            byte[] reserved = new byte[10];
            byte[] auth2 = randomBytes(12);
            byte[] plugin = strz("mysql_native_password");

            byte[] buf = concat(
                    new byte[]{0x0A}, // protocol version
                    serverVersion,
                    new byte[]{1,0,0,0}, // connection id
                    auth1,
                    filler,
                    le2(capLower & 0xFFFF),
                    new byte[]{charset},
                    le2(status),
                    le2((capUpper >> 16) & 0xFFFF), // capability upper 2 bytes (protocol uses 4+4 bytes)
                    new byte[]{authLen},
                    reserved,
                    auth2,
                    plugin
            );
            return buf;
        }

        private byte[] okPacket() {
            return concat(new byte[]{0x00},
                    lenEncInt(0), // affected rows
                    lenEncInt(0), // last insert id
                    le2(0x0002), // status flags: autocommit
                    le2(0)); // warnings
        }

        private byte[] errorPacket(int code, String msg) {
            return concat(new byte[]{(byte)0xFF},
                    le2(code),
                    new byte[]{'#'},
                    "HY000".getBytes(StandardCharsets.UTF_8),
                    msg.getBytes(StandardCharsets.UTF_8));
        }

        private byte[][] singleColumnResult(String colName, String value) {
            return resultSet(new String[]{colName}, new String[][]{{value}});
        }

        private byte[][] resultSet(String[] columns, String[][] rows) {
            int seq = 0;
            byte[][] packets = new byte[2 + columns.length + rows.length + 1][]; // header + cols + EOF + rows + EOF
            // resultset header: column count as lenenc
            packets[seq++] = lenEncInt(columns.length);
            // column definitions
            for (String c : columns) {
                packets[seq++] = columnDefPacket(c);
            }
            // EOF
            packets[seq++] = eofPacket();
            // rows
            for (String[] r : rows) {
                packets[seq++] = rowPacket(r);
            }
            // EOF
            packets[seq] = eofPacket();
            return packets;
        }

        private byte[] columnDefPacket(String name) {
            // ColumnDefinition41
            byte[] payload = concat(
                    lenEncStr("def"), // catalog
                    lenEncStr(""),    // schema
                    lenEncStr(""),    // table
                    lenEncStr(""),    // org_table
                    lenEncStr(name),   // name
                    lenEncStr(name),   // org_name
                    new byte[]{(byte)0x0C}, // length of fixed-length fields
                    le2(0x21),         // character set (utf8)
                    le4(256),          // column length
                    new byte[]{(byte)0xfd}, // type = VAR_STRING
                    le2(0),            // flags
                    new byte[]{0},     // decimals
                    new byte[]{0,0}    // filler
            );
            return payload;
        }

        private byte[] eofPacket() {
            return concat(new byte[]{(byte)0xFE}, le2(0), le2(0));
        }

        private byte[] rowPacket(String[] cols) {
            byte[] buf = new byte[0];
            for (String c : cols) buf = concat(buf, lenEncStr(c));
            return buf;
        }

        // --- Byte helpers ---
        private static byte[] strz(String s) {
            byte[] b = s.getBytes(StandardCharsets.UTF_8);
            byte[] z = new byte[b.length + 1];
            System.arraycopy(b, 0, z, 0, b.length);
            z[z.length - 1] = 0;
            return z;
        }

        private byte[] randomBytes(int n) {
            byte[] b = new byte[n];
            rnd.nextBytes(b);
            return b;
        }

        private static byte[] concat(byte[]... parts) {
            int len = 0;
            for (byte[] p : parts) len += p.length;
            byte[] r = new byte[len];
            int off = 0;
            for (byte[] p : parts) {
                System.arraycopy(p, 0, r, off, p.length);
                off += p.length;
            }
            return r;
        }

        private static byte[] le2(int v) { return new byte[]{(byte)(v & 0xFF), (byte)((v >> 8) & 0xFF)}; }
        private static byte[] le4(int v) {
            return new byte[]{(byte)(v & 0xFF), (byte)((v >> 8) & 0xFF), (byte)((v >> 16) & 0xFF), (byte)((v >> 24) & 0xFF)};
        }

        private static byte[] lenEncInt(int v) {
            if (v < 251) return new byte[]{(byte)v};
            if (v < (1 << 16)) return concat(new byte[]{(byte)0xFC}, le2(v));
            return concat(new byte[]{(byte)0xFE}, le4(v));
        }

        private static byte[] lenEncStr(String s) {
            byte[] b = s.getBytes(StandardCharsets.UTF_8);
            return concat(lenEncInt(b.length), b);
        }
    }
}
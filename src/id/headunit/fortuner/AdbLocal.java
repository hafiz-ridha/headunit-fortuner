package id.headunit.fortuner;

import android.content.Context;
import android.util.Base64;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;

import javax.crypto.Cipher;

/**
 * Klien ADB minimal ke adbd headunit sendiri (127.0.0.1:5555) untuk menjalankan perintah shell yang tidak boleh
 * dijalankan aplikasi biasa, di sini HANYA untuk mengatur jendela picture-in-picture Google Maps.
 * Berfungsi selama ADB lewat jaringan (port 5555) terbuka. Saat pertama dipakai, headunit menampilkan
 * "Allow USB debugging" untuk kunci milik launcher ini.
 */
final class AdbLocal {
    private static final int CNXN = 0x4e584e43, AUTH = 0x48545541, OPEN = 0x4e45504f, OKAY = 0x59414b4f,
            WRTE = 0x45545257, CLSE = 0x45534c43;
    private static final int VERSION = 0x01000000, MAX_DATA = 4096;
    private static final int AUTH_TOKEN = 1, AUTH_SIGNATURE = 2, AUTH_RSAPUBLICKEY = 3;
    private static final byte[] SHA1_DIGEST_INFO = {0x30, 0x21, 0x30, 0x09, 0x06, 0x05, 0x2b, 0x0e, 0x03, 0x02, 0x1a,
            0x05, 0x00, 0x04, 0x14};

    /** True setelah sekali berhasil masuk (kunci launcher sudah diizinkan headunit). */
    static volatile boolean authorized;

    private AdbLocal() { }

    /**
     * Menjalankan satu perintah shell; mengembalikan keluarannya. Lempar IOException bila ADB tidak tersedia.
     * allowPrompt: boleh memunculkan dialog "Allow USB debugging" bila kunci belum diizinkan (menunggu ≤60 dtk).
     */
    static synchronized String shell(Context c, String command, int timeoutMs, boolean allowPrompt) throws IOException {
        KeyPair keys = keys(c);
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress("127.0.0.1", 5555), 2000);
            s.setSoTimeout(timeoutMs);
            OutputStream out = s.getOutputStream();
            DataInputStream in = new DataInputStream(s.getInputStream());
            send(out, CNXN, VERSION, MAX_DATA, "host::\0".getBytes(StandardCharsets.UTF_8));
            Message m = read(in);
            if (m.command == AUTH && m.arg0 == AUTH_TOKEN) {
                // Coba tanda tangan dengan kunci yang mungkin sudah diizinkan sebelumnya.
                send(out, AUTH, AUTH_SIGNATURE, 0, sign(keys.getPrivate(), m.data));
                m = read(in);
                if (m.command == AUTH) {
                    // Belum diizinkan: kirim kunci publik, headunit menampilkan "Allow USB debugging".
                    if (!allowPrompt) throw new IOException("ADB lokal belum diizinkan");
                    send(out, AUTH, AUTH_RSAPUBLICKEY, 0, publicKeyBytes((RSAPublicKey) keys.getPublic()));
                    s.setSoTimeout(60_000);
                    m = read(in);
                    s.setSoTimeout(timeoutMs);
                }
            }
            if (m.command != CNXN) throw new IOException("ADB lokal menolak otorisasi");
            authorized = true;

            send(out, OPEN, 1, 0, ("shell:" + command + "\0").getBytes(StandardCharsets.UTF_8));
            ByteArrayOutputStream result = new ByteArrayOutputStream();
            int remote = 0;
            while (true) {
                Message r = read(in);
                if (r.command == OKAY) {
                    remote = r.arg0;
                } else if (r.command == WRTE) {
                    result.write(r.data);
                    send(out, OKAY, 1, r.arg0, new byte[0]);
                } else if (r.command == CLSE) {
                    send(out, CLSE, 1, remote, new byte[0]);
                    break;
                }
            }
            return new String(result.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    /** True bila port ADB lokal sedang terbuka (tanpa otorisasi). */
    static boolean portOpen() {
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress("127.0.0.1", 5555), 700);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    // ---- protokol ----

    private static final class Message {
        int command, arg0, arg1;
        byte[] data;
    }

    private static void send(OutputStream out, int cmd, int arg0, int arg1, byte[] data) throws IOException {
        ByteBuffer b = ByteBuffer.allocate(24 + data.length).order(ByteOrder.LITTLE_ENDIAN);
        int sum = 0;
        for (byte d : data) sum += d & 0xff;
        b.putInt(cmd).putInt(arg0).putInt(arg1).putInt(data.length).putInt(sum).putInt(cmd ^ 0xffffffff);
        b.put(data);
        out.write(b.array());
        out.flush();
    }

    private static Message read(DataInputStream in) throws IOException {
        byte[] h = new byte[24];
        in.readFully(h);
        ByteBuffer b = ByteBuffer.wrap(h).order(ByteOrder.LITTLE_ENDIAN);
        Message m = new Message();
        m.command = b.getInt();
        m.arg0 = b.getInt();
        m.arg1 = b.getInt();
        int len = b.getInt();
        if (len < 0 || len > 1024 * 1024) throw new IOException("ADB lokal: paket rusak");
        m.data = new byte[len];
        in.readFully(m.data);
        return m;
    }

    // ---- kunci RSA ----

    private static byte[] sign(PrivateKey key, byte[] token) throws IOException {
        try {
            byte[] digestInfo = new byte[SHA1_DIGEST_INFO.length + token.length];
            System.arraycopy(SHA1_DIGEST_INFO, 0, digestInfo, 0, SHA1_DIGEST_INFO.length);
            System.arraycopy(token, 0, digestInfo, SHA1_DIGEST_INFO.length, token.length);
            Cipher c = Cipher.getInstance("RSA/ECB/PKCS1Padding");
            c.init(Cipher.ENCRYPT_MODE, key);
            return c.doFinal(digestInfo);
        } catch (GeneralSecurityException e) {
            throw new IOException("Tanda tangan ADB gagal", e);
        }
    }

    /** Kunci publik dalam format adb (struct RSAPublicKey Android, base64) + nama perangkat. */
    private static byte[] publicKeyBytes(RSAPublicKey pub) {
        final int words = 64; // 2048 bit
        BigInteger n = pub.getModulus();
        BigInteger r32 = BigInteger.ONE.shiftLeft(32);
        BigInteger n0inv = n.mod(r32).modInverse(r32).negate().mod(r32);
        BigInteger rr = BigInteger.ONE.shiftLeft(4096).mod(n);
        ByteBuffer b = ByteBuffer.allocate(4 + 4 + words * 4 + words * 4 + 4).order(ByteOrder.LITTLE_ENDIAN);
        b.putInt(words).putInt(n0inv.intValue());
        for (int i = 0; i < words; i++) b.putInt(n.shiftRight(32 * i).mod(r32).intValue());
        for (int i = 0; i < words; i++) b.putInt(rr.shiftRight(32 * i).mod(r32).intValue());
        b.putInt(pub.getPublicExponent().intValue());
        String s = Base64.encodeToString(b.array(), Base64.NO_WRAP) + " fortuner-launcher@headunit\0";
        return s.getBytes(StandardCharsets.UTF_8);
    }

    private static KeyPair keys(Context c) throws IOException {
        File dir = c.getFilesDir();
        File priv = new File(dir, "adbkey"), pub = new File(dir, "adbkey.pub");
        try {
            KeyFactory kf = KeyFactory.getInstance("RSA");
            if (priv.exists() && pub.exists()) {
                return new KeyPair(kf.generatePublic(new X509EncodedKeySpec(readAll(pub))),
                        kf.generatePrivate(new PKCS8EncodedKeySpec(readAll(priv))));
            }
            KeyPairGenerator g = KeyPairGenerator.getInstance("RSA");
            g.initialize(2048);
            KeyPair kp = g.generateKeyPair();
            writeAll(priv, kp.getPrivate().getEncoded());
            writeAll(pub, kp.getPublic().getEncoded());
            return kp;
        } catch (GeneralSecurityException e) {
            throw new IOException("Kunci ADB tidak bisa dibuat", e);
        }
    }

    private static byte[] readAll(File f) throws IOException {
        try (InputStream in = new FileInputStream(f)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return out.toByteArray();
        }
    }

    private static void writeAll(File f, byte[] data) throws IOException {
        try (FileOutputStream out = new FileOutputStream(f)) {
            out.write(data);
        }
        Log.i(AppLauncherRepository.TAG, "Kunci ADB lokal dibuat: " + f.getName());
    }
}

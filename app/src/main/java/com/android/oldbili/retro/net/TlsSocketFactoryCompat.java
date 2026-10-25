package com.android.oldbili.retro.net;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.X509TrustManager;

/**
 * 老设备 TLS 兼容的 SocketFactory。
 *
 * 安卓2.x~4.x 系统自带的 SSL 栈其实支持 TLS1.1/TLS1.2，只是默认不开启，
 * 而 B 站服务器现在只认 TLS1.2+，所以老设备直连会出现
 * "有的机器能上、有的机器握手失败、有的机器慢得离谱"。
 *
 * 这里在每个连接建立时显式打开系统支持的最高 TLS 协议，并把现代加密套件排到前面。
 * 只提升协议与套件，不改变证书校验逻辑。
 */
public class TlsSocketFactoryCompat extends SSLSocketFactory {

    private final SSLSocketFactory mDefault;
    private String[] mProtocols;
    private String[] mCipherSuites;

    public TlsSocketFactoryCompat(X509TrustManager trustManager) {
        try {
            SSLContext sslContext = SSLContext.getInstance("TLS");
            if (trustManager != null) {
                sslContext.init(null, new X509TrustManager[]{trustManager}, null);
            } else {
                sslContext.init(null, null, null);
            }
            mDefault = sslContext.getSocketFactory();
            probe();
        } catch (Exception e) {
            // 个别 ROM 上拿不到默认工厂，往上抛会让整个网络层初始化失败
            throw new IllegalStateException(e);
        }
    }

    /** 探测系统支持的最高 TLS 协议与可用套件，失败就保持原样 */
    private void probe() {
        try {
            SSLSocket socket = (SSLSocket) mDefault.createSocket();
            List<String> supported = Arrays.asList(socket.getSupportedProtocols());
            List<String> picked = new ArrayList<String>();
            String[] wanted = {"TLSv1.2", "TLSv1.1", "TLSv1"};
            for (int i = 0; i < wanted.length; i++) {
                if (supported.contains(wanted[i])) {
                    picked.add(wanted[i]);
                }
            }
            mProtocols = picked.toArray(new String[0]);

            List<String> ciphers = new ArrayList<String>();
            List<String> modern = Arrays.asList(
                    "TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256",
                    "TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256",
                    "TLS_ECDHE_ECDSA_WITH_AES_128_CBC_SHA",
                    "TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA",
                    "TLS_RSA_WITH_AES_128_CBC_SHA",
                    "TLS_RSA_WITH_AES_256_CBC_SHA",
                    "TLS_RSA_WITH_3DES_EDE_CBC_SHA");
            List<String> available = Arrays.asList(socket.getSupportedCipherSuites());
            HashSet<String> availSet = new HashSet<String>(available);
            for (int i = 0; i < modern.size(); i++) {
                if (availSet.contains(modern.get(i)) && !ciphers.contains(modern.get(i))) {
                    ciphers.add(modern.get(i));
                }
            }
            String[] enabled = socket.getEnabledCipherSuites();
            for (int i = 0; i < enabled.length; i++) {
                if (!ciphers.contains(enabled[i])) {
                    ciphers.add(enabled[i]);
                }
            }
            mCipherSuites = ciphers.toArray(new String[0]);
            try {
                socket.close();
            } catch (Exception ignored) {
            }
        } catch (Exception e) {
            mProtocols = null;
            mCipherSuites = null;
        }
    }

    private Socket upgrade(Socket socket) {
        if (socket instanceof SSLSocket) {
            SSLSocket ssl = (SSLSocket) socket;
            try {
                if (mProtocols != null && mProtocols.length > 0) {
                    ssl.setEnabledProtocols(mProtocols);
                }
            } catch (Exception ignored) {
            }
            try {
                if (mCipherSuites != null && mCipherSuites.length > 0) {
                    ssl.setEnabledCipherSuites(mCipherSuites);
                }
            } catch (Exception ignored) {
            }
        }
        return socket;
    }

    @Override
    public String[] getDefaultCipherSuites() {
        return mCipherSuites;
    }

    @Override
    public String[] getSupportedCipherSuites() {
        return mCipherSuites;
    }

    @Override
    public Socket createSocket(Socket s, String host, int port, boolean autoClose) throws IOException {
        return upgrade(mDefault.createSocket(s, host, port, autoClose));
    }

    @Override
    public Socket createSocket(String host, int port) throws IOException {
        return upgrade(mDefault.createSocket(host, port));
    }

    @Override
    public Socket createSocket(String host, int port, InetAddress localHost, int localPort) throws IOException {
        return upgrade(mDefault.createSocket(host, port, localHost, localPort));
    }

    @Override
    public Socket createSocket(InetAddress host, int port) throws IOException {
        return upgrade(mDefault.createSocket(host, port));
    }

    @Override
    public Socket createSocket(InetAddress address, int port, InetAddress localAddress, int localPort) throws IOException {
        return upgrade(mDefault.createSocket(address, port, localAddress, localPort));
    }
}

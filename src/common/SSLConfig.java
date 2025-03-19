package common;

import javax.net.ssl.*;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.*;
import java.security.cert.CertificateException;

public class SSLConfig {
    private static final String KEYSTORE_PATH = "keystore.jks";
    private static final String KEYSTORE_PASSWORD = "changeit";
    private static final String KEY_PASSWORD = "changeit";
    
    public static SSLServerSocketFactory createServerSocketFactory() throws SSLException {
        try {
            KeyStore keyStore = KeyStore.getInstance("JKS");
            keyStore.load(new FileInputStream(KEYSTORE_PATH), KEYSTORE_PASSWORD.toCharArray());
            
            KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            kmf.init(keyStore, KEY_PASSWORD.toCharArray());
            
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(kmf.getKeyManagers(), null, null);
            
            return sslContext.getServerSocketFactory();
        } catch (KeyStoreException | NoSuchAlgorithmException | CertificateException | IOException | UnrecoverableKeyException | KeyManagementException e) {
            throw new SSLException("Erreur lors de la configuration SSL: " + e.getMessage());
        }
    }
    
    public static SSLSocketFactory createClientSocketFactory() throws SSLException {
        try {
            TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            KeyStore keyStore = KeyStore.getInstance("JKS");
            keyStore.load(new FileInputStream(KEYSTORE_PATH), KEYSTORE_PASSWORD.toCharArray());
            tmf.init(keyStore);
            
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, tmf.getTrustManagers(), null);
            
            return sslContext.getSocketFactory();
        } catch (KeyStoreException | NoSuchAlgorithmException | CertificateException | IOException | KeyManagementException e) {
            throw new SSLException("Erreur lors de la configuration SSL: " + e.getMessage());
        }
    }
} 
package com.datastax.quickstart;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.CqlSessionBuilder;
import com.datastax.oss.driver.api.core.config.DriverConfigLoader;
import com.datastax.oss.driver.api.core.metadata.EndPoint;
import com.datastax.oss.driver.api.core.ssl.ProgrammaticSslEngineFactory;
import com.datastax.oss.driver.api.core.ssl.SslEngineFactory;

import javax.net.ssl.*;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class SSLTest {

    private static List<String> NODES = Arrays.asList("ip-10-101-32-211.srv101.dsinternal.org");

    public static void main(String[] args) {
        CqlSessionBuilder builder = null;
        //BY CODE
        try {
            builder = CqlSession.builder()
                    .addContactPoints(NODES.stream().map((hn) -> new InetSocketAddress(hn, 9042)).collect(Collectors.toList()))
                    .withAuthCredentials("cassandra", "cassandra")
                    //// CHOOSE ONE, 3 options are equivalent (hostname validation is false)
                    .withSslContext(createSslContext())
                    //.withSslEngineFactory(new ProgrammaticSslEngineFactory(createSslContext()))
                    //.withSslEngineFactory(new ProgrammaticSslEngineFactory(createSslContext(),null, false))
                    //// END
                    // this one activate hostname validation
                    //.withSslEngineFactory(new ProgrammaticSslEngineFactory(createSslContext(), null, true))
                    .withLocalDatacenter("datacenter1");
        } catch (GeneralSecurityException e) {
            // Problem creating SSL context
            e.printStackTrace();
        } catch (IOException e) {
            // Problem with supplied keystore files
            e.printStackTrace();
        }

        //BY CONFIGURATION
        /*
        builder = CqlSession.builder()
          //rename application.example.conf to application.conf to remove this line
            .withConfigLoader(DriverConfigLoader.fromFile(new File("application.example.conf")));

         */

        try (CqlSession session = builder.build()) {
            // Session is successfully created
            System.out.println("Successfully connected to cluster "+session.getMetadata().getClusterName().orElse("unknown"));

            /// NEXT CAN BE DELETED, just print if host validation is enabled
            Optional<SslEngineFactory> sslEngineFactoryOpt = session.getContext().getSslEngineFactory();
            if (sslEngineFactoryOpt.isPresent()) {
                SslEngineFactory sslEngineFactory = sslEngineFactoryOpt.get();
                SSLEngine engine = sslEngineFactory.newSslEngine(new EndPoint() {
                    @Override
                    public SocketAddress resolve() {
                        return new InetSocketAddress(NODES.get(0), 9042);
                    }

                    @Override
                    public String asMetricPrefix() {
                        return "";
                    }
                });
                SSLParameters sslParameters = engine.getSSLParameters();
                String endpointIdentificationAlgorithm = sslParameters.getEndpointIdentificationAlgorithm();
                if(endpointIdentificationAlgorithm == null){
                    System.out.println("host validation disabled");
                }
                //IF algorithm is HTTPS, then endpoint identification is enabled
                else if ("HTTPS".equals(endpointIdentificationAlgorithm))
                    System.out.println("host validation enabled");
                //Is it possible ?
                else System.out.println("Unknown SSL configuration state");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    /* this context directly load keystore and truststore files
     */
    static SSLContext createSslContext() throws GeneralSecurityException, IOException {
        FileInputStream keyStoreFile = new FileInputStream("keystore.jks");
        FileInputStream trustStoreFile = new FileInputStream("truststore.jks");
        KeyStore keyStore = KeyStore.getInstance("JKS");
        keyStore.load(keyStoreFile, "ctool_keystore".toCharArray());
        KeyStore trustStore = KeyStore.getInstance("JKS");
        trustStore.load(trustStoreFile, "ctool_truststore".toCharArray());

        //initialize Trust manager directly from its truststore
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(trustStore);
        //initialize Key Manager manager directly from its keystore
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(keyStore, "ctool_keystore".toCharArray());
        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), new SecureRandom());
        return sslContext;
    }

}

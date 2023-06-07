package com.example.demo;

import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.LoopResources;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.util.Base64;

@SpringBootApplication
public class DemoApplication {

	private static final HttpClient httpClient = HttpClient.create().runOn(LoopResources.create("reactor-webclient"));

	public static void main(String[] args) throws IOException, NoSuchAlgorithmException, UnrecoverableKeyException, KeyStoreException {
		String artifactoryUrl = "https://artifactory.moderne.ninja/artifactory";
		String username = System.getenv("ARTIFACTORY_USER");
		String password = System.getenv("ARTIFACTORY_PASSWORD");
		String repo = "moderne-ingest"; //System.getenv("ARTIFACTORY_REPO")

		WebClient.Builder webClientBuilder = WebClient.builder();

		SslContextBuilder sslContextBuilder = SslContextBuilder
				.forClient()
				.trustManager(InsecureTrustManagerFactory.INSTANCE);


//		String certPath = System.getenv("SSL_CERT_PATH");
//		String keyPath = System.getenv("SSL_KEY_PATH");
//		if (keyPath == null) {
//			keyPath = certPath;
//		}
//
//		if (certPath != null) {
//			sslContextBuilder = sslContextBuilder.keyManager(new File(certPath), new File(keyPath));
//		}

		// If the client keystore exists, use it to configure the client SSL context
		String javaHome = System.getenv("JAVA_HOME");
		if (javaHome != null) {
			char[] keyStorePassword = "changeit".toCharArray();  // Default password for the keystore
			String clientKeyStore = javaHome + "/lib/security/client_keystore.p12";
			if (new File(clientKeyStore).exists()) {
				KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());

				try (FileInputStream fis = new FileInputStream(clientKeyStore)) {
					keyStore.load(fis, keyStorePassword);
				} catch (CertificateException | NoSuchAlgorithmException e) {
					throw new RuntimeException(e);
				}

				KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
				keyManagerFactory.init(keyStore, keyStorePassword);

				sslContextBuilder.keyManager(keyManagerFactory);
			}
		}

		SslContext sslContext = sslContextBuilder.build();
		WebClient.Builder builderWithSSL = webClientBuilder
				.clone()
				.clientConnector(new ReactorClientHttpConnector(httpClient.secure(t -> t.sslContext(sslContext))));

		builderWithSSL.clone()
				.baseUrl(artifactoryUrl)
				.build()
				.post()
				.uri("/api/search/aql")
				.contentType(MediaType.TEXT_PLAIN)
				.header("Authorization", "Basic " + Base64.getEncoder().encodeToString(
						(username + ":" + password).getBytes(StandardCharsets.UTF_8))
				)
				.bodyValue(String.format("""
      					items.find({"name":{"$match":"*-ast.jar"}, "repo":{"$eq": "%s"},"modified":{"$gt":"2023-06-06T18:32:36.47577639Z"}}).include("name","repo","path","modified").limit(1000)
					""", repo)
				)
				.retrieve()
				.bodyToMono(String.class)
				.flatMap(s -> {
					System.out.println(s);
					return null;
				})
				.block();

		System.exit(0);
	}

}

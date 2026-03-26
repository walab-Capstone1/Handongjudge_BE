package com.project.handongjudge.config;

import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Profiles;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Opens a local port forward to Redis over SSH before the context refreshes,
 * so {@code spring.redis} can connect to {@code localhost} safely.
 * Skipped for {@code prod} (dev/deploy servers) and {@code test} (JUnit).
 */
public class SshTunnelApplicationContextInitializer
		implements ApplicationContextInitializer<ConfigurableApplicationContext> {

	private static volatile Session session;

	@Override
	public void initialize(ConfigurableApplicationContext applicationContext) {
		ConfigurableEnvironment env = applicationContext.getEnvironment();
		if (env.acceptsProfiles(Profiles.of("deploy", "test"))) {
			return;
		}
		if (!Boolean.parseBoolean(env.getProperty("app.ssh-tunnel.enabled", "true"))) {
			return;
		}
		String host = env.getProperty("app.ssh-tunnel.host");
		String user = env.getProperty("app.ssh-tunnel.user");
		if (host == null || host.isBlank() || user == null || user.isBlank()) {
			throw new IllegalStateException(
					"app.ssh-tunnel.host and app.ssh-tunnel.user must be set for local development (no prod profile)");
		}
		String remoteRedisHost = env.getProperty("app.ssh-tunnel.remote-redis-host");
		if (remoteRedisHost == null || remoteRedisHost.isBlank()) {
			throw new IllegalStateException(
					"app.ssh-tunnel.remote-redis-host must be set for local development (no prod profile)");
		}

		int sshPort = Integer.parseInt(env.getProperty("app.ssh-tunnel.port", "22"));
		String sshPassword = env.getProperty("app.ssh-tunnel.password");
		String identityPath = env.getProperty("app.ssh-tunnel.identity-path");
		if (identityPath == null || identityPath.isBlank()) {
			identityPath = Paths.get(System.getProperty("user.home"), ".ssh", "id_rsa").toString();
		}
		int localPort = Integer.parseInt(env.getProperty("app.ssh-tunnel.local-port", "6379"));
		int remoteRedisPort = Integer.parseInt(env.getProperty("app.ssh-tunnel.remote-redis-port", "6379"));

		try {
			JSch jsch = new JSch();
			Session s;
			if (sshPassword != null && !sshPassword.isBlank()) {
				s = jsch.getSession(user, host, sshPort);
				s.setConfig("StrictHostKeyChecking", "no");
				s.setPassword(sshPassword);
				s.setConfig("PreferredAuthentications", "password,keyboard-interactive,publickey");
			} else {
				Path keyFile = Paths.get(identityPath);
				if (!Files.isRegularFile(keyFile)) {
					throw new IllegalStateException(
							"SSH tunnel: set SSH_TUNNEL_PASSWORD or a valid SSH_IDENTITY_PATH (file missing: "
									+ keyFile + ")");
				}
				jsch.addIdentity(identityPath);
				s = jsch.getSession(user, host, sshPort);
				s.setConfig("StrictHostKeyChecking", "no");
			}
			s.connect();
			s.setPortForwardingL(localPort, remoteRedisHost, remoteRedisPort);
			session = s;
		} catch (JSchException e) {
			throw new IllegalStateException("SSH tunnel failed (local Redis). Check app.ssh-tunnel.* (password or key).", e);
		}

		applicationContext.addApplicationListener((ApplicationListener<ContextClosedEvent>) event -> disconnect());
		Runtime.getRuntime().addShutdownHook(new Thread(SshTunnelApplicationContextInitializer::disconnect));
	}

	private static synchronized void disconnect() {
		if (session != null && session.isConnected()) {
			session.disconnect();
		}
		session = null;
	}
}

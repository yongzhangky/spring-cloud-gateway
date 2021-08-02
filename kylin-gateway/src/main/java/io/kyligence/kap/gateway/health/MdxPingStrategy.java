package io.kyligence.kap.gateway.health;

import com.google.common.collect.Lists;
import com.netflix.loadbalancer.IPing;
import com.netflix.loadbalancer.IPingStrategy;
import com.netflix.loadbalancer.Server;
import io.kyligence.kap.gateway.config.MdxConfig;
import io.kyligence.kap.gateway.event.KylinRefreshRoutesEvent;
import io.micrometer.core.instrument.util.NamedThreadFactory;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.ArrayUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationListener;

import javax.annotation.PostConstruct;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static io.kyligence.kap.gateway.health.MdxLoad.*;


/**
 * @author liang.xu
 */

@Slf4j
@Data
public class MdxPingStrategy implements IPingStrategy, ApplicationListener<KylinRefreshRoutesEvent> {

	private Map<Server, AtomicInteger> serversStatus = new ConcurrentHashMap<>();

	private ExecutorService executorService = Executors.newCachedThreadPool();

	private final ScheduledExecutorService pingRefresher;

	private int retryTimes;

	private int intervalSeconds;

	private int generateSchemaIntervalSeconds;

	private IPing ping;

	@Autowired
	private MdxConfig mdxConfig;

	public MdxPingStrategy(IPing ping) {
		this.ping = ping;

		this.pingRefresher = Executors.newSingleThreadScheduledExecutor(new NamedThreadFactory("PingRefresher"));

	}

	@PostConstruct
	public void init() {
		pingRefresher.scheduleWithFixedDelay(this::generateSchema, 0, generateSchemaIntervalSeconds, TimeUnit.SECONDS);
		pingRefresher.scheduleWithFixedDelay(this::pingServers, 10, intervalSeconds > 0 ? intervalSeconds : 3, TimeUnit.SECONDS);
	}

	private void pingServers() {
		try {
			pingServers(Lists.newArrayList(serversStatus.keySet()));
		} catch (Exception e) {
			log.error("Failed to run cron ping servers", e);
		}
	}

	public synchronized boolean[] pingServers(List<Server> servers) {
		if (CollectionUtils.isEmpty(servers)) {
			return new boolean[]{false};
		}

		boolean[] results = new boolean[servers.size()];

		Future[] futures = new Future[servers.size()];

		for (int i = 0; i < servers.size(); i++) {
			futures[i] = executorService.submit(new CheckServerTask(this.ping, servers.get(i)));
		}

		for (int i = 0; i < servers.size(); i++) {
			try {
				results[i] = (Boolean) futures[i].get();
			} catch (Exception e) {
				log.error("Task execute failed, server: {}", servers.get(i));
			}
		}

		return results;
	}

	private void generateSchema() {
		try {
			List<String> serverList = new LinkedList<>();
			for (MdxConfig.ProxyInfo proxyInfo : mdxConfig.getProxy()) {
				serverList.addAll(proxyInfo.getServers());
			}
			List<Server> serverList1 = serverList.stream().map(s -> new Server(s)).collect(Collectors.toList());
			generateAllServerSchema(serverList1);
		} catch (Exception e) {
			log.error("Failed to run cron generate schema", e);
		}
	}

	public void generateAllServerSchema(List<Server> servers) {
		if (CollectionUtils.isEmpty(servers)) {
			return;
		}
		for (int i = 0; i < servers.size(); i++) {
			executorService.submit(new GenerateSchemaTask(this.ping, servers.get(i)));
		}
	}

	@Override
	public boolean[] pingServers(IPing ping, Server[] servers) {
		if (ArrayUtils.isEmpty(servers)) {
			log.debug("Ping servers is empty!");
			return new boolean[]{false};
		}

		boolean[] results = new boolean[servers.length];

		List<Server> notCachedServers = Lists.newArrayList();
		for (int i = 0; i < servers.length; i++) {
			if (serversStatus.containsKey(servers[i])) {
				AtomicInteger errorTimes = serversStatus.get(servers[i]);
				results[i] = Objects.nonNull(errorTimes) && errorTimes.get() < retryTimes;
			} else {
				notCachedServers.add(servers[i]);
			}
		}

		if (notCachedServers.isEmpty()) {
			return results;
		}

		synchronized (MdxPingStrategy.class) {
			pingServers(notCachedServers);

			for (int i = 0; i < servers.length; i++) {
				AtomicInteger errorTimes = serversStatus.get(servers[i]);
				results[i] = Objects.nonNull(errorTimes) && errorTimes.get() < retryTimes;
			}
		}

		return results;
	}

	@Override
	public synchronized void onApplicationEvent(KylinRefreshRoutesEvent event) {
		for (Server server : serversStatus.keySet()) {
			if (serversStatus.get(server).get() > retryTimes) {
				removeServer(server.getId());
			}
		}
		Set<Server> removeList = serversStatus.keySet().stream().filter(server -> !event.getServerSet().contains(server)).collect(Collectors.toSet());
		removeList.forEach(serversStatus::remove);
	}

	private class CheckServerTask implements Callable<Boolean> {

		private IPing ping;

		private Server server;

		private CheckServerTask(IPing ping, Server server) {
			this.ping = ping;
			this.server = server;
		}

		@Override
		public Boolean call() {
			serversStatus.putIfAbsent(server, new AtomicInteger(retryTimes));
			AtomicInteger errorTimes = serversStatus.get(server);

			try {
				if (ping instanceof MdxPing) {
					switch (((MdxPing) ping).checkServer(server)) {
						case NORMAL:
							errorTimes.set(0);
							double load = ((MdxPing) ping).getServerLoad(server);
							updateServerByMemLoad(server.getId(), load);
							return true;
						case FATAL:
							// stop route immediately
							if (errorTimes.get() < retryTimes - 1) {
								errorTimes.set(retryTimes - 1);
							}
							break;
						case ERROR:
							// check 2 times
							if (errorTimes.get() < retryTimes - 2) {
								errorTimes.set(retryTimes - 2);
							}
							break;
						case WARN:
						default:
							break;
					}
				} else if (ping.isAlive(server)) {
					errorTimes.set(0);
					return true;
				}
			} catch (Exception e) {
				log.error("Failed to ping server: {}", server, e);
			}

			return errorTimes.incrementAndGet() < retryTimes;
		}
	}

	private class GenerateSchemaTask implements Runnable {

		private IPing ping;

		private Server server;

		private GenerateSchemaTask(IPing ping, Server server) {
			this.ping = ping;
			this.server = server;
		}

		@Override
		public void run() {
			if (ping instanceof MdxPing) {
				((MdxPing) ping).generateSchema(server);
			}
		}
	}

}

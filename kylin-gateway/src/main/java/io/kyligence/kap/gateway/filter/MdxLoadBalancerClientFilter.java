package io.kyligence.kap.gateway.filter;

import com.netflix.loadbalancer.BaseLoadBalancer;
import com.netflix.loadbalancer.ILoadBalancer;
import com.netflix.loadbalancer.Server;
import io.kyligence.kap.gateway.health.MdxLoad;
import io.kyligence.kap.gateway.utils.MdxAuthenticationUtils;
import io.kyligence.kap.gateway.utils.TimeUtil;
import io.netty.util.concurrent.DefaultThreadFactory;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.loadbalancer.LoadBalancerClient;
import org.springframework.cloud.gateway.config.LoadBalancerProperties;
import org.springframework.cloud.gateway.event.RefreshRoutesEvent;
import org.springframework.cloud.gateway.filter.LoadBalancerClientFilter;
import org.springframework.cloud.gateway.support.ipresolver.RemoteAddressResolver;
import org.springframework.cloud.gateway.support.ipresolver.XForwardedRemoteAddressResolver;
import org.springframework.cloud.netflix.ribbon.RibbonLoadBalancerClient;
import org.springframework.context.ApplicationListener;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.server.ServerWebExchange;

import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
public class MdxLoadBalancerClientFilter extends LoadBalancerClientFilter
		implements ApplicationListener<RefreshRoutesEvent> {

	private static final Logger monitorLog = LoggerFactory.getLogger("monitor");

	private Map<String, MdxLoadBalancer> resourceGroups = new ConcurrentHashMap<>();

	private final RemoteAddressResolver remoteAddressResolver = XForwardedRemoteAddressResolver.trustAll();

	/**
	 * server cache
	 * key: BI request: user_project, normal request: host
	 * value: ServerInfo, contains: server ip:port, cache start time and cache update time
	 */
	public static final Map<String, ServerInfo> serverMap = new ConcurrentHashMap<>();

	private final ScheduledExecutorService scheduledExecService = new ScheduledThreadPoolExecutor(1,
			new DefaultThreadFactory("schedule-server"));

	private static final String X_HOST = "X-Host";

	private static final String X_PORT = "X-Port";

	private static final int IP_PORT_LENGTH = 2;

	private static final double REALLOCATE_NUM = 1.2;

	public MdxLoadBalancerClientFilter(LoadBalancerClient loadBalancer,
									   LoadBalancerProperties properties) {
		super(loadBalancer, properties);
		scheduledExecService.scheduleWithFixedDelay(
				new ScheduledClearServer(),
				0,
				30,
				TimeUnit.SECONDS
		);
	}

	public ILoadBalancer getLoadBalancer(String serviceId) {
		return resourceGroups.get(serviceId);
	}

	@Override
	protected ServiceInstance choose(ServerWebExchange exchange) {
		ServerHttpRequest request = exchange.getRequest();
		HttpHeaders httpHeaders = request.getHeaders();
		String hostName = "";
		List<String> hosts = httpHeaders.get(HttpHeaders.HOST);
		if (CollectionUtils.isNotEmpty(hosts)) {
			hostName = hosts.get(0);
		}
		String[] tmp = hostName.split(":");
		if (tmp.length < 2) {
			hostName = tmp[0] + ":80";
		}

		// Diagnostic pack request, routed by X-Host and X-Port
		if (httpHeaders.get(X_HOST) != null && httpHeaders.get(X_PORT) != null) {
			String xHost = httpHeaders.get(X_HOST).get(0);
			String xPort = httpHeaders.get(X_PORT).get(0);
			if (StringUtils.isNotBlank(xHost) && StringUtils.isNotBlank(xPort)) {
				Server server = new Server(xHost, Integer.parseInt(xPort));
				MdxLoad.updateServerByQueryNum(server.getId(), 1);
				return new RibbonLoadBalancerClient.RibbonServer(hostName, server);
			}
		}

		// BI request, routed by user and project
		String projectName = null;
		String userName = null;
		try {
			String contextPath = MdxAuthenticationUtils.getProjectContext(request.getPath().toString());
			projectName = MdxAuthenticationUtils.getProjectName(contextPath);
			userName = MdxAuthenticationUtils.getUsername(request);
		} catch (Exception e) {
			// Nothing to do
		}
		String serverKey = "";
		if (StringUtils.isNotBlank(projectName) && StringUtils.isNotBlank(userName)) {
			serverKey = userName.toUpperCase() + "_" + projectName;
			ServiceInstance serviceInstance = getServiceInstance(hostName, serverKey);
			if (serviceInstance != null) {
				MdxLoad.updateServerByQueryNum(serviceInstance.getUri().getAuthority(), 1);
				return serviceInstance;
			}
		}

		// Normal request, routed by request host
		InetSocketAddress remoteAddress = remoteAddressResolver.resolve(exchange);
		if (StringUtils.isBlank(serverKey) && remoteAddress != null) {
			serverKey = remoteAddress.getHostString();
			ServiceInstance serviceInstance = getServiceInstance(hostName, serverKey);
			if (serviceInstance != null) {
				MdxLoad.updateServerByQueryNum(serviceInstance.getUri().getAuthority(), 1);
				return serviceInstance;
			}
		}

		return choose(hostName, serverKey, null);
	}

	@Override
	public void onApplicationEvent(RefreshRoutesEvent event) {
		// Nothing to do
	}

	@Override
	public void updateResourceGroups(List<BaseLoadBalancer> updateResourceGroups, final long mvcc) {
		ConcurrentHashMap<String, MdxLoadBalancer> newResourceGroups = new ConcurrentHashMap<>();

		updateResourceGroups.forEach(resourceGroup -> {
			if (resourceGroup instanceof MdxLoadBalancer) {
				MdxLoadBalancer mdxLoadBalancer = ((MdxLoadBalancer) resourceGroup);
				newResourceGroups.put(mdxLoadBalancer.getServiceId(), mdxLoadBalancer);
			}
		});

		Collection<MdxLoadBalancer> oldResourceGroups = resourceGroups.values();
		resourceGroups = newResourceGroups;
		oldResourceGroups.stream().filter(lb -> lb.getMvcc() < mvcc).forEach(MdxLoadBalancer::shutdown);

		for (MdxLoadBalancer loadBalancer : newResourceGroups.values()) {
			log.info("Saved LoadBalancer: {}", loadBalancer);
		}
	}

	@Override
	public void addResourceGroups(List<BaseLoadBalancer> addResourceGroups) {
		addResourceGroups.forEach(resourceGroup -> {
			if (resourceGroup instanceof MdxLoadBalancer) {
				MdxLoadBalancer mdxLoadBalancer = ((MdxLoadBalancer) resourceGroup);
				resourceGroups.putIfAbsent(mdxLoadBalancer.getServiceId(), mdxLoadBalancer);
			}
		});
	}

	@Override
	public Map<String, Object> getLoadBalancerServers() {
		return resourceGroups.values().stream().collect(Collectors.toMap(MdxLoadBalancer::getServiceId, value -> Arrays.toString(value.getAllServers().toArray())));
	}

	public ServiceInstance getServiceInstance(String hostName, String serverKey) {
		ServerInfo serverInfo = serverMap.get(serverKey);
		if (serverInfo != null && serverInfo.getServer() != null) {
			String[] ipPort = serverInfo.getServer().split(":");
			if (ipPort.length == IP_PORT_LENGTH) {
				Server server = new Server(ipPort[0], Integer.parseInt(ipPort[1]));
				ServerInfo newServerInfo = new ServerInfo(serverInfo.getServer(), serverInfo.getStartTime(), TimeUtil.getSecondTime());
				serverMap.put(serverKey, newServerInfo);
				return new RibbonLoadBalancerClient.RibbonServer(hostName, server);
			}
		}
		return null;
	}

	public ServiceInstance choose(String serviceId, String serverKey, Object hint) {
		Server server = getServer(getLoadBalancer(serviceId), hint);
		if (server == null) {
			log.warn("Host: {}, 503, Service Unavailable!", serviceId);
			return null;
		}
		MdxLoad.updateServerByQueryNum(server.getId(), 1);
		ServerInfo serverInfo = new ServerInfo(server.getId(), TimeUtil.getSecondTime(), TimeUtil.getSecondTime());
		serverMap.put(serverKey, serverInfo);
		return new RibbonLoadBalancerClient.RibbonServer(serviceId, server);
	}

	protected Server getServer(ILoadBalancer loadBalancer, Object hint) {

		if (loadBalancer == null) {
			return null;
		}

		// choose node server by cluster server load
		List<Server> serverList = loadBalancer.getAllServers();
		Server chooseServer = null;
		double lowLoad = Double.MAX_VALUE;
		for (Server server : serverList) {
			MdxLoad.LoadInfo loadInfo = MdxLoad.LOAD_INFO_MAP.get(server.getId());
			if (loadInfo == null || loadInfo.getNodeLoad() == null) {
				continue;
			}
			double nodeLoad = loadInfo.getNodeLoad();
			if (nodeLoad < lowLoad) {
				chooseServer = server;
				lowLoad = nodeLoad;
			}
		}
		if (chooseServer != null) {
			return chooseServer;
		}

		// Use 'default' on a null hint, or just pass it on
		return loadBalancer.chooseServer(hint != null ? hint : "default");
	}

	@Data
	@AllArgsConstructor
	public static class ServerInfo {

		private String server;

		private long startTime;

		private long updateTime;

	}

	public class ScheduledClearServer implements Runnable {
		@Override
		public void run() {
			try {
				cleanExpireCache();
				printMonitorInfo();
				reallocateServerCache();
			} catch (Exception e) {
				log.error("schedule catch exception: ", e);
			}
		}

		/**
		 * clean expired server which not used in two period
		 */
		private void cleanExpireCache() {
			for (Map.Entry<String, ServerInfo> entry : serverMap.entrySet()) {
				ServerInfo serverInfo = entry.getValue();
				if (serverInfo.getUpdateTime() - serverInfo.getStartTime() > MdxLoad.getCacheTime()) {
					serverMap.remove(serverInfo.getServer());
				}
			}
		}

		/**
		 * print route cache and cluster load information
		 */
		private void printMonitorInfo() {
			monitorLog.info("--------- server route cache info start ----------");
			for (Map.Entry<String, ServerInfo> entry : serverMap.entrySet()) {
				ServerInfo serverInfo = entry.getValue();
				if (serverInfo != null) {
					monitorLog.info(" cache key: {} route to {}", entry.getKey(), serverInfo.getServer());
				}
			}
			monitorLog.info("--------- server route cache info end ----------");

			monitorLog.info("--------- cluster load info start ----------");
			for (Map.Entry<String, MdxLoad.LoadInfo> entry : MdxLoad.LOAD_INFO_MAP.entrySet()) {
				MdxLoad.LoadInfo loadInfo = entry.getValue();
				if (loadInfo != null) {
					monitorLog.info(" node server is : {},  load is: {}, memory load is: {}, query num is: {}",
							entry.getKey(), loadInfo.getNodeLoad(), loadInfo.getMemLoad(),
							loadInfo.getQueryLoad());
				}
			}
			monitorLog.info("--------- cluster load info end ----------");
		}

		/**
		 * avoid query store in one node
		 */
		private void reallocateServerCache() {
			Map<String, Double> tmp = new HashMap<>();
			double querySum = 0D;
			for (Map.Entry<String, MdxLoad.LoadInfo> entry : MdxLoad.LOAD_INFO_MAP.entrySet()) {
				MdxLoad.LoadInfo loadInfo = entry.getValue();
				if (loadInfo != null) {
					querySum += loadInfo.getQueryLoad();
					tmp.put(entry.getKey(), loadInfo.getQueryLoad());
				}
			}

			double avgQuery = querySum / MdxLoad.LOAD_INFO_MAP.size();
			for (Map.Entry<String, Double> entry : tmp.entrySet()) {
				if (entry.getValue() / avgQuery > REALLOCATE_NUM) {
					tmp.put(entry.getKey(), entry.getValue() - avgQuery);
				}
			}

			for (Map.Entry<String, ServerInfo> entry : serverMap.entrySet()) {
				ServerInfo serverInfo = entry.getValue();
				if (serverInfo == null) {
					continue;
				}
				String serverId = serverInfo.getServer();
				double needRemoveNum = tmp.getOrDefault(serverId, 0D);
				if (needRemoveNum < 1) {
					continue;
				}
				serverMap.remove(entry.getKey());
				needRemoveNum--;
				tmp.put(serverId, needRemoveNum);
			}
		}
	}
}

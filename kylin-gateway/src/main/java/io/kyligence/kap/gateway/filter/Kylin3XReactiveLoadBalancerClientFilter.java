package io.kyligence.kap.gateway.filter;

import com.netflix.loadbalancer.DummyPing;
import com.netflix.loadbalancer.ILoadBalancer;
import com.netflix.loadbalancer.RoundRobinRule;
import com.netflix.loadbalancer.Server;
import io.kyligence.kap.gateway.utils.AsyncQueryUtil;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.loadbalancer.LoadBalancerClient;
import org.springframework.cloud.gateway.config.LoadBalancerProperties;
import org.springframework.cloud.gateway.filter.LoadBalancerClientFilter;
import org.springframework.cloud.netflix.ribbon.RibbonLoadBalancerClient;
import org.springframework.web.server.ServerWebExchange;

import java.net.URI;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR;

public class Kylin3XReactiveLoadBalancerClientFilter extends LoadBalancerClientFilter {

	private static final String ASYNC_SUFFIX = "/async_query";

	private Map<String, Kylin3XLoadBalancer> resourceGroup = new ConcurrentHashMap<>();

	public Kylin3XReactiveLoadBalancerClientFilter(LoadBalancerClient loadBalancer, LoadBalancerProperties properties) {
		super(loadBalancer, properties);

		Kylin3XLoadBalancer balancer = new Kylin3XLoadBalancer("USER-SERVER2", new DummyPing(), new RoundRobinRule());
		balancer.addServer(new Server("10.1.2.56:7070"));
		balancer.addServer(new Server("10.1.2.166:7070"));
		balancer.addServer(new Server("10.1.2.167:7070"));
		balancer.addServer(new Server("10.1.2.168:7070"));

		Kylin3XLoadBalancer balancerAsync = new Kylin3XLoadBalancer("USER-SERVER2-ASYNC", new DummyPing(), new RoundRobinRule());
		balancer.addServer(new Server("10.1.2.56:7070"));
		balancer.addServer(new Server("10.1.2.166:7070"));
		balancer.addServer(new Server("10.1.2.167:7070"));
		balancer.addServer(new Server("10.1.2.168:7070"));

		resourceGroup.put(balancer.getServiceId(), balancer);
		resourceGroup.put(balancer.getServiceId(), balancerAsync);
	}

	public ILoadBalancer getLoadBalancer(String serviceId) {
		return resourceGroup.get(serviceId);
	}

	protected Server getServer(ILoadBalancer loadBalancer, Object hint) {
		if (loadBalancer == null) {
			return null;
		}
		// Use 'default' on a null hint, or just pass it on?
		return loadBalancer.chooseServer(hint != null ? hint : "default");
	}

	public ServiceInstance choose(String serviceId, Object hint) {
		Server server = getServer(getLoadBalancer(serviceId), hint);
		if (server == null) {
			return null;
		}
		return new RibbonLoadBalancerClient.RibbonServer(serviceId, server);
	}

	@Override
	protected ServiceInstance choose(ServerWebExchange exchange) {
		URI uri = exchange.getAttribute(GATEWAY_REQUEST_URL_ATTR);
		if (null == uri) {
			return null;
		}

		ServiceInstance serviceInstance = null;
		if (uri.getPath().endsWith(ASYNC_SUFFIX)) {
			serviceInstance = choose(AsyncQueryUtil.buildAsyncQueryServiceId(uri.getHost()), AsyncQueryUtil.ASYNC_QUERY_SUFFIX_TAG);
		}

		return serviceInstance != null ? serviceInstance : choose(uri.getHost(), null);
	}
}

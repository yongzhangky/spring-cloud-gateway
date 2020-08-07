package io.kyligence.kap.gateway.filter;

import com.google.common.collect.Lists;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.cloud.client.ServiceInstance;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

public class Kylin3XRoundRobinLoadBalancer {

	private static final Log log = LogFactory.getLog(Kylin3XRoundRobinLoadBalancer.class);

	private final AtomicInteger position;

	private final String serviceId;

	private List<ServiceInstance> instances = new ArrayList<>();

	public Kylin3XRoundRobinLoadBalancer(String serviceId) {
		this.serviceId = serviceId;
		this.position = new AtomicInteger(new Random().nextInt(1000));
	}

	public boolean updateInstance(Set<ServiceInstance> instances) {
		this.instances = Lists.newArrayList(instances);
		return true;
	}

	public ServiceInstance defaultInstance() {
		return null;
	}

	public ServiceInstance getInstance() {
		if (instances.isEmpty()) {
			log.warn("No servers available for service: " + this.serviceId);
			return null;
		}

		int pos = Math.abs(this.position.incrementAndGet());

		return instances.get(pos % instances.size());
	}

	public String getServiceId() {
		return this.serviceId;
	}
}

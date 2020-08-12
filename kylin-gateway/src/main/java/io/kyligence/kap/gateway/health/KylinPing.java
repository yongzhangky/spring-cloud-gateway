package io.kyligence.kap.gateway.health;

import com.netflix.loadbalancer.IPing;
import com.netflix.loadbalancer.Server;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Objects;

@Component
@Slf4j
public class KylinPing implements IPing {
	private static final String HEALTH_URL_FROMAT = "http://%s%s";

	@Autowired
	private RestTemplate restTemplate;

	@Value("${kylin.gateway.health.check-url:/kylin/api/health}")
	private String healthUrl;

	@Override
	public boolean isAlive(Server server) {
		if (Objects.isNull(server)) {
			return false;
		}
		String healthCheckUrl = String.format(HEALTH_URL_FROMAT, server.getId(), healthUrl);
		try {
			ResponseEntity<String> responseEntity = restTemplate.getForEntity(healthCheckUrl, String.class);
			if (responseEntity.getStatusCode().is2xxSuccessful()) {
				return true;
			}
		} catch (Exception e) {
			log.error("health check failed, url: {}.", healthCheckUrl, e);
		}
		return false;
	}
}

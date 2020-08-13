package io.kyligence.kap.gateway.entity;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.loadbalancer.Server;
import io.kyligence.kap.gateway.persistent.domain.KylinRouteDO;
import io.kyligence.kap.gateway.persistent.domain.RouteDO;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Data
@Slf4j
@NoArgsConstructor
public class KylinRouteRaw {

	private long id;

	private String stringBackends;

	private List<Server> backends;

	private String project;

	private String resourceGroup;

	private String type;

	private String cluster;

	private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

	public KylinRouteRaw(KylinRouteDO kylinRouteDO) {
		this.id = kylinRouteDO.getId();
		this.project = kylinRouteDO.getProject();
		this.resourceGroup = kylinRouteDO.getResourceGroup();
		this.type = kylinRouteDO.getType();
		this.cluster = kylinRouteDO.getCluster();
		this.stringBackends = kylinRouteDO.getBackends();
		try {
			List<String> instances = OBJECT_MAPPER.readValue(kylinRouteDO.getBackends(), List.class);
			backends = instances.stream().map(Server::new).collect(Collectors.toList());
		} catch (JsonProcessingException e) {
			log.error("Failed to read backends.", e);
		}
	}

	public static KylinRouteRaw convert(KylinRouteDO kylinRouteDO) {
		if (null == kylinRouteDO) {
			log.error("Failed to convert KylinRouteDO to KylinRouteRaw, cause by kylinRouteDO is null!");
			return null;
		}

		return new KylinRouteRaw(kylinRouteDO);
	}

	public static KylinRouteRaw convert(RouteDO routeDO) {
		if (null == routeDO) {
			return null;
		}

		KylinRouteRaw kylinRouteRaw = new KylinRouteRaw();

		kylinRouteRaw.setId(routeDO.getId());
		kylinRouteRaw.setType(routeDO.getType());
		kylinRouteRaw.setProject(routeDO.getProject());
		kylinRouteRaw.setResourceGroup(routeDO.getResourceGroup());
		kylinRouteRaw.setStringBackends(Arrays.toString(routeDO.getBackends().toArray()));
		kylinRouteRaw.setBackends(routeDO.getBackends().stream().map(Server::new).collect(Collectors.toList()));

		return kylinRouteRaw;
	}

	@Override
	public String toString() {
		return "KylinRouteRaw{" + "id=" + id + ", backends='" + stringBackends + '\''
				+ ", project='" + project + '\'' + ", resourceGroup='" + resourceGroup
				+ '\'' + ", type='" + type + '\'' + '}';
	}

}

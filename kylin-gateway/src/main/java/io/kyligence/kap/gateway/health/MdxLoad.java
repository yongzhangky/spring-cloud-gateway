package io.kyligence.kap.gateway.health;

import io.kyligence.kap.gateway.filter.MdxLoadBalancerClientFilter;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author liang.xu
 */
@Slf4j
@Component
public class MdxLoad {

	/**
	 * key: server
	 * value: loadInfo
	 */
	public static final Map<String, LoadInfo> LOAD_INFO_MAP = new ConcurrentHashMap<>();

	private static Double memWeight;

	private static Double queryWeight;

	private static Long cacheTime;

	private static Long serverSize;

	private static double threshold;

	public static void updateServerByMemLoad(String serverId, double memLoad) {
		if (StringUtils.isBlank(serverId)) {
			return;
		}

		if (memLoad > threshold) {
			for (Map.Entry<String, MdxLoadBalancerClientFilter.ServerInfo> entry :
					MdxLoadBalancerClientFilter.serverMap.entrySet()) {
				MdxLoadBalancerClientFilter.ServerInfo serverInfo = entry.getValue();
				if (serverInfo == null) {
					continue;
				}
				if (serverId.equals(serverInfo.getServer())) {
					MdxLoadBalancerClientFilter.serverMap.remove(entry.getKey());
				}
			}
		}
		LoadInfo loadInfo = LOAD_INFO_MAP.get(serverId);
		if (loadInfo == null) {
			loadInfo = new LoadInfo(0D, 0D, 0D);
		}
		double nodeLoad = memLoad * memWeight + loadInfo.getQueryLoad() / serverSize * queryWeight;
		loadInfo.setMemLoad(memLoad);
		loadInfo.setNodeLoad(nodeLoad);
		LOAD_INFO_MAP.put(serverId, loadInfo);
	}

	public static void updateServerByQueryNum(String serverId, double queryNum) {
		if (StringUtils.isBlank(serverId)) {
			return;
		}
		LoadInfo loadInfo = LOAD_INFO_MAP.get(serverId);
		if (loadInfo == null) {
			loadInfo = new LoadInfo(0D, 0D, 0D);
		}
		queryNum = loadInfo.getQueryLoad() + queryNum;
		if (queryNum < 0) {
			return;
		}
		double nodeLoad = loadInfo.getMemLoad() * memWeight + queryNum / serverSize * queryWeight;
		loadInfo.setQueryLoad(queryNum);
		loadInfo.setNodeLoad(nodeLoad);
		LOAD_INFO_MAP.put(serverId, loadInfo);
	}

	public static void removeServer(String serverKey) {
		LOAD_INFO_MAP.remove(serverKey);
		for (Map.Entry<String, MdxLoadBalancerClientFilter.ServerInfo> entry :
				MdxLoadBalancerClientFilter.serverMap.entrySet()) {
			MdxLoadBalancerClientFilter.ServerInfo serverInfo = entry.getValue();
			if (serverInfo == null) {
				continue;
			}
			if (serverKey.equals(serverInfo.getServer())) {
				MdxLoadBalancerClientFilter.serverMap.remove(entry.getKey());
			}
		}
		log.info("removed server: {} from active server list", serverKey);
	}

	@Data
	@AllArgsConstructor
	public static class LoadInfo {

		private Double memLoad;

		private Double queryLoad;

		private Double nodeLoad;
	}

	@Value(value = "${mdx.weight.memory}")
	public void setMemWeight(Double memWeight) {
		MdxLoad.memWeight = memWeight;
	}

	@Value(value = "${mdx.weight.query}")
	public void setQueryWeight(Double queryWeight) {
		MdxLoad.queryWeight = queryWeight;
	}

	@Value(value = "${mdx.cacheTime}")
	public void setCacheTime(long cacheTime) {
		MdxLoad.cacheTime = cacheTime;
	}

	@Value(value = "${mdx.serverSize}")
	public void setServerSize(long serverSize) {
		MdxLoad.serverSize = serverSize;
	}

	@Value("${mdx.weight.threshold:0.9}")
	private void setThreshold(double threshold) {
		MdxLoad.threshold = threshold;
	}

	public static long getCacheTime() {
		return cacheTime;
	}
}

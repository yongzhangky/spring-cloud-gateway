package io.kyligence.kap.gateway.route.reader;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
import io.kyligence.kap.gateway.entity.KylinRouteRaw;
import io.kyligence.kap.gateway.persistent.FileDataSource;
import io.kyligence.kap.gateway.persistent.domain.RouteDO;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.util.List;

@Slf4j
public class FileRouteTableReader implements IRouteTableReader {

	//TODO Save inMemory route table to file .
	private String routeTableFile;

	private ObjectMapper routeDOMapper = new ObjectMapper();

	public FileRouteTableReader(FileDataSource fileDataSource) {
		this.routeTableFile = fileDataSource.getRouteTableFilePath();
	}

	@Override
	public List<KylinRouteRaw> list() {
		List<KylinRouteRaw> result = Lists.newArrayList();
		try {
			List<String> routeTable = FileUtils.readLines(new File(routeTableFile));
			for (String route : routeTable) {
				result.add(KylinRouteRaw.convert(routeDOMapper.readValue(route, RouteDO.class)));
			}
		} catch (JsonProcessingException e) {
			log.error("Route format example: {\"id\":4, \"backends\":[\"10.1.2.56:7070\"], \"project\":\"p2\", \"resourceGroup\":\"common_query_1\", \"type\":\"CUBE\"}", e);
		} catch (Exception e) {
			log.error("Failed to read route table from file: {}", routeTableFile, e);
		}

		return result;
	}
}

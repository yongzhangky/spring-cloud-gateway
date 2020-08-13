package io.kyligence.kap.gateway.route.writer;

import io.kyligence.kap.gateway.entity.KylinRouteRaw;

import java.util.List;

public interface IRouteTableWriter {

	// TODO user update route table by rest api.
	boolean update(List<KylinRouteRaw> routeTable);

}

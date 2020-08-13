package io.kyligence.kap.gateway.route.writer;

import io.kyligence.kap.gateway.entity.KylinRouteRaw;

import java.util.List;

public class MockRouteTableWriter implements IRouteTableWriter {
	@Override
	public boolean update(List<KylinRouteRaw> routeTable) {
		return false;
	}
}

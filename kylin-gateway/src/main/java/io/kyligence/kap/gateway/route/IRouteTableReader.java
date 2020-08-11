package io.kyligence.kap.gateway.route;

import io.kyligence.kap.gateway.entity.KylinRouteRaw;

import java.util.List;

public interface IRouteTableReader {

	List<KylinRouteRaw> list();

}

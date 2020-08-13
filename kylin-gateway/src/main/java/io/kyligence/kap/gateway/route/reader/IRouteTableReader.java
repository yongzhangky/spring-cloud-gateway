package io.kyligence.kap.gateway.route.reader;

import io.kyligence.kap.gateway.entity.KylinRouteRaw;

import java.util.List;

public interface IRouteTableReader {

	List<KylinRouteRaw> list();

}

package io.kyligence.kap.gateway.route;

import com.google.common.collect.Lists;
import io.kyligence.kap.gateway.entity.KylinRouteRaw;
import io.kyligence.kap.gateway.persistent.domain.KylinRouteDO;

import java.util.List;

public class MockRouteTableReader implements IRouteTableReader {

	@Override
	public List<KylinRouteRaw> list() {
		List<KylinRouteRaw> kylinRouteRawList = Lists.newArrayList();

		kylinRouteRawList.add(new KylinRouteRaw(new KylinRouteDO(1, "[\"10.1.2.56:7070\"]",
				"p1", "common_query_1", "CUBE", "1c4b3f35-21f9-44b2-a2de-ae2d5a94189f")));

		kylinRouteRawList.add(new KylinRouteRaw(new KylinRouteDO(2, "[\"10.1.2.56:7070\"]",
				"p1", "common_query_2", "ASYNC", "1c4b3f35-21f9-44b2-a2de-ae2d5a94189f")));

		kylinRouteRawList.add(new KylinRouteRaw(new KylinRouteDO(3, "[\"10.1.2.56:7070\"]",
				"p2", "default", "GLOBAL", "1c4b3f35-21f9-44b2-a2de-ae2d5a94189f")));

		kylinRouteRawList.add(new KylinRouteRaw(new KylinRouteDO(4, "[\"10.1.2.56:7070\"]",
				"p2", "common_query_1", "CUBE", "1c4b3f35-21f9-44b2-a2de-ae2d5a94189f")));

		return kylinRouteRawList;
	}

}

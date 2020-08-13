package io.kyligence.kap.gateway.route.reader;

import com.google.common.collect.Lists;
import io.kyligence.kap.gateway.entity.KylinRouteRaw;

import java.util.List;

// todo
public class FtpRouteTableReader implements IRouteTableReader {

	@Override
	public List<KylinRouteRaw> list() {
		return Lists.newArrayList();
	}
}

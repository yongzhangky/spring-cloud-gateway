package io.kyligence.kap.gateway.exception;

import org.springframework.boot.web.error.ErrorAttributeOptions;
import org.springframework.boot.web.reactive.error.DefaultErrorAttributes;
import org.springframework.web.reactive.function.server.ServerRequest;

import java.util.Map;

public class KylinErrorAttributes extends DefaultErrorAttributes {

	@Override
	public Map<String, Object> getErrorAttributes(ServerRequest request, ErrorAttributeOptions options) {
		Map<String, Object> msg = getErrorAttributes(request, options.isIncluded(ErrorAttributeOptions.Include.STACK_TRACE));
		if (msg != null && msg.get("data") == null) {
			msg.put("data", msg.get("message"));
		}
		return msg;
	}
}

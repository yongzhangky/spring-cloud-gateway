/*
 * Copyright 2013-2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.kyligence.kap.gateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;

import java.util.HashMap;
import java.util.function.Predicate;

/**
 * @author zhiyu.zeng
 */

@SpringBootApplication
public class BootstrapServer {

	public static void main(String[] args) {
		SpringApplication.run(BootstrapServer.class, args);
	}

	public Predicate<String> myPredicate() {
		return r -> {
			try {
				HashMap json = new ObjectMapper().readValue(r, HashMap.class);
				for (Object key : json.keySet()) {
					if (key instanceof String
							&& "project".equalsIgnoreCase((String) key)) {
						if ("p1".equalsIgnoreCase((String) json.get(key))) {
							return true;
						}
					}
				}
			}
			catch (Exception e) {

			}

			return false;
		};
	}

	public RouteLocator testRouteLoacator(RouteLocatorBuilder builder) {
		return builder.routes()
				.route("test_route", r -> r.header("project", "p1").or()
						.query("project", "p1").or().readBody(String.class, myPredicate())
						.filters(f -> f.addRequestHeader("Gateway", "test_gateway"))
						.uri("http://10.1.2.56:7070"))
				.build();
	}

}

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

import io.kyligence.kap.gateway.entity.KylinRouteRaw;
import io.kyligence.kap.gateway.persistent.KylinRouteStore;
import io.kyligence.kap.gateway.persistent.domain.KylinRouteDO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.util.List;

/**
 * @author zhiyu.zeng
 */

@SpringBootApplication
public class BootstrapServer implements CommandLineRunner {
	@Autowired
	KylinRouteStore kylinRouteStore;

	public static void main(String[] args) {
		SpringApplication.run(BootstrapServer.class, args);
	}

	@Bean
	@ConfigurationProperties(prefix = "kylin.rest.connection")
	public HttpComponentsClientHttpRequestFactory httpRequestFactory() {
		return new HttpComponentsClientHttpRequestFactory();
	}

	@Bean
	public RestTemplate restTemplate() {
		RestTemplate restTemplate = new RestTemplate();
		restTemplate.setRequestFactory(httpRequestFactory());
		return restTemplate;
	}

	public void testGetKylinRouteRow() {
		KylinRouteDO kylinRouteDO = new KylinRouteDO(1, "[\"10.1.2.56:7070\",\"10.1.2.166:7070\",\"10.1.2.167:7070\"]",
				"project1", "common_query_1", "CUBE", "1c4b3f35-21f9-44b2-a2de-ae2d5a94189f");
		KylinRouteRaw kylinRouteRaw = new KylinRouteRaw(kylinRouteDO);
		System.out.println(kylinRouteRaw.getBackends());
	}

	@Override
	public void run(String... args) throws Exception {
		testGetKylinRouteRow();
	}
}

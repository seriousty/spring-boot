/*
 * Copyright 2012-2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.boot.endpoint.web;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.assertj.core.api.Condition;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import org.springframework.boot.endpoint.Endpoint;
import org.springframework.boot.endpoint.EndpointInfo;
import org.springframework.boot.endpoint.EndpointType;
import org.springframework.boot.endpoint.ReadOperation;
import org.springframework.boot.endpoint.Selector;
import org.springframework.boot.endpoint.WriteOperation;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.convert.support.DefaultConversionService;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link WebAnnotationEndpointDiscoverer}.
 *
 * @author Andy Wilkinson
 * @author Stephane Nicoll
 */
public class WebAnnotationEndpointDiscovererTests {

	@Rule
	public final ExpectedException thrown = ExpectedException.none();

	@Test
	public void discoveryWorksWhenThereAreNoEndpoints() {
		load(EmptyConfiguration.class, (discoverer) -> {
			assertThat(discoverer.discoverEndpoints()).isEmpty();
		});
	}

	@Test
	public void webExtensionMustHaveEndpoint() {
		load(TestWebEndpointExtensionConfiguration.class, (discoverer) -> {
			this.thrown.expect(IllegalStateException.class);
			this.thrown.expectMessage("Invalid extension");
			this.thrown.expectMessage(TestWebEndpointExtension.class.getName());
			this.thrown.expectMessage("no endpoint found");
			this.thrown.expectMessage(TestEndpoint.class.getName());
			discoverer.discoverEndpoints();
		});
	}

	@Test
	public void onlyWebEndpointsAreDiscovered() {
		load(MultipleEndpointsConfiguration.class, discoverer -> {
			Collection<EndpointInfo<WebEndpointOperation>> endpoints = discoverer
					.discoverEndpoints();
			assertThat(endpoints).hasSize(1);
			EndpointInfo<WebEndpointOperation> endpoint = endpoints.iterator().next();
			assertThat(endpoint.getId()).isEqualTo("test");
		});
	}

	@Test
	public void oneOperationIsDiscoveredWhenExtensionOverridesOperation() {
		load(OverriddenOperationWebEndpointExtensionConfiguration.class, (discoverer) -> {
			Collection<EndpointInfo<WebEndpointOperation>> endpoints = discoverer
					.discoverEndpoints();
			assertThat(endpoints).hasSize(1);
			EndpointInfo<WebEndpointOperation> endpoint = endpoints.iterator().next();
			assertThat(endpoint.getId()).isEqualTo("test");
			assertThat(requestPredicates(endpoint)).has(requestPredicates(
					path("/application/test").httpMethod(WebEndpointHttpMethod.GET)));
		});
	}

	@Test
	public void twoOperationsAreDiscoveredWhenExtensionAddsOperation() {
		load(AdditionalOperationWebEndpointConfiguration.class, (discoverer) -> {
			Collection<EndpointInfo<WebEndpointOperation>> endpoints = discoverer
					.discoverEndpoints();
			assertThat(endpoints).hasSize(1);
			EndpointInfo<WebEndpointOperation> endpoint = endpoints.iterator().next();
			assertThat(endpoint.getId()).isEqualTo("test");
			assertThat(requestPredicates(endpoint)).has(requestPredicates(
					path("/application/test").httpMethod(WebEndpointHttpMethod.GET),
					path("/application/test/{id}")
							.httpMethod(WebEndpointHttpMethod.GET)));
		});
	}

	@Test
	public void discoveryFailsWhenTwoExtensionsHaveTheSameEndpointType() {
		load(ClashingWebEndpointConfiguration.class, (discoverer) -> {
			this.thrown.expect(IllegalStateException.class);
			this.thrown.expectMessage("Found two extensions for the same endpoint");
			this.thrown.expectMessage(TestEndpoint.class.getName());
			this.thrown.expectMessage(TestWebEndpointExtension.class.getName());
			discoverer.discoverEndpoints();
		});
	}

	@Test
	public void discoveryFailsWhenTwoStandardEndpointsHaveTheSameId() {
		load(ClashingStandardEndpointConfiguration.class, (discoverer) -> {
			this.thrown.expect(IllegalStateException.class);
			this.thrown.expectMessage("Found two endpoints with the id 'test': ");
			discoverer.discoverEndpoints();
		});
	}

	@Test
	public void discoveryFailsWhenEndpointHasClashingOperations() {
		load(ClashingOperationsEndpointConfiguration.class, (discoverer) -> {
			this.thrown.expect(IllegalStateException.class);
			this.thrown.expectMessage(
					"Found multiple web operations with matching request predicates:");
			discoverer.discoverEndpoints();
		});
	}

	@Test
	public void discoveryFailsWhenExtensionIsNotCompatibleWithTheEndpointType() {
		load(InvalidWebExtensionConfiguration.class, discoverer -> {
			this.thrown.expect(IllegalStateException.class);
			this.thrown.expectMessage("Invalid extension");
			this.thrown.expectMessage(NonWebWebEndpointExtension.class.getName());
			this.thrown.expectMessage(NonWebEndpoint.class.getName());
			discoverer.discoverEndpoints();
		});
	}

	@Test
	public void twoOperationsOnSameEndpointClashWhenSelectorsHaveDifferentNames() {
		load(ClashingSelectorsWebEndpointExtensionConfiguration.class, (discoverer) -> {
			this.thrown.expect(IllegalStateException.class);
			this.thrown.expectMessage(
					"Found multiple web operations with matching request predicates:");
			discoverer.discoverEndpoints();
		});
	}

	@Test
	public void anEmptyBasePathExposesEndpointsAtTheRoot() {
		load("", TestEndpointConfiguration.class, (discoverer) -> {
			Collection<EndpointInfo<WebEndpointOperation>> endpoints = discoverer
					.discoverEndpoints();
			assertThat(endpoints).hasSize(1);
			EndpointInfo<WebEndpointOperation> endpoint = endpoints.iterator().next();
			assertThat(endpoint.getId()).isEqualTo("test");
			assertThat(requestPredicates(endpoint)).has(requestPredicates(
					path("/test").httpMethod(WebEndpointHttpMethod.GET)));
		});
	}

	@Test
	public void singleSlashBasePathExposesEndpointsAtTheRoot() {
		load("/", TestEndpointConfiguration.class, (discoverer) -> {
			Collection<EndpointInfo<WebEndpointOperation>> endpoints = discoverer
					.discoverEndpoints();
			assertThat(endpoints).hasSize(1);
			EndpointInfo<WebEndpointOperation> endpoint = endpoints.iterator().next();
			assertThat(endpoint.getId()).isEqualTo("test");
			assertThat(requestPredicates(endpoint)).has(requestPredicates(
					path("/test").httpMethod(WebEndpointHttpMethod.GET)));
		});
	}

	private void load(Class<?> configuration,
			Consumer<WebAnnotationEndpointDiscoverer> consumer) {
		this.load("application", configuration, consumer);
	}

	private void load(String basePath, Class<?> configuration,
			Consumer<WebAnnotationEndpointDiscoverer> consumer) {
		AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(
				configuration);
		try {
			consumer.accept(new WebAnnotationEndpointDiscoverer(context,
					DefaultConversionService.getSharedInstance(), basePath,
					Arrays.asList("application/json"),
					Arrays.asList("application/json")));
		}
		finally {
			context.close();
		}
	}

	private List<OperationRequestPredicate> requestPredicates(
			EndpointInfo<WebEndpointOperation> endpoint) {
		return endpoint.getOperations().stream()
				.map(operation -> operation.getRequestPredicate())
				.collect(Collectors.toList());
	}

	private Condition<List<? extends OperationRequestPredicate>> requestPredicates(
			RequestPredicateMatcher... matchers) {
		return new Condition<List<? extends OperationRequestPredicate>>(predicates -> {
			if (predicates.size() != matchers.length) {
				return false;
			}
			Map<OperationRequestPredicate, Long> matchCounts = new HashMap<>();
			for (OperationRequestPredicate predicate : predicates) {
				matchCounts.put(predicate, Stream.of(matchers)
						.filter(matcher -> matcher.matches(predicate)).count());
			}
			return !matchCounts.values().stream().anyMatch(count -> count != 1);
		}, Arrays.toString(matchers));
	}

	private RequestPredicateMatcher path(String path) {
		return new RequestPredicateMatcher(path);
	}

	@Configuration
	static class EmptyConfiguration {

	}

	@WebEndpointExtension(endpoint = TestEndpoint.class)
	static class TestWebEndpointExtension {

		@ReadOperation
		public Object getAll() {
			return null;
		}

		@ReadOperation
		public Object getOne(@Selector String id) {
			return null;
		}

		@WriteOperation
		public void update(String foo, String bar) {

		}

		public void someOtherMethod() {

		}

	}

	@Endpoint(id = "test")
	static class TestEndpoint {

		@ReadOperation
		public Object getAll() {
			return null;
		}

	}

	@WebEndpointExtension(endpoint = TestEndpoint.class)
	static class OverriddenOperationWebEndpointExtension {

		@ReadOperation
		public Object getAll() {
			return null;
		}

	}

	@WebEndpointExtension(endpoint = TestEndpoint.class)
	static class AdditionalOperationWebEndpointExtension {

		@ReadOperation
		public Object getOne(@Selector String id) {
			return null;
		}

	}

	@Endpoint(id = "test")
	static class ClashingOperationsEndpoint {

		@ReadOperation
		public Object getAll() {
			return null;
		}

		@ReadOperation
		public Object getAgain() {
			return null;
		}

	}

	@WebEndpointExtension(endpoint = TestEndpoint.class)
	static class ClashingOperationsWebEndpointExtension {

		@ReadOperation
		public Object getAll() {
			return null;
		}

		@ReadOperation
		public Object getAgain() {
			return null;
		}

	}

	@WebEndpointExtension(endpoint = TestEndpoint.class)
	static class ClashingSelectorsWebEndpointExtension {

		@ReadOperation
		public Object readOne(@Selector String oneA, @Selector String oneB) {
			return null;
		}

		@ReadOperation
		public Object readTwo(@Selector String twoA, @Selector String twoB) {
			return null;
		}

	}

	@Endpoint(id = "nonweb", types = EndpointType.JMX)
	static class NonWebEndpoint {

		@ReadOperation
		public Object getData() {
			return null;
		}

	}

	@WebEndpointExtension(endpoint = NonWebEndpoint.class)
	static class NonWebWebEndpointExtension {

		@ReadOperation
		public Object getSomething(@Selector String name) {
			return null;
		}

	}


	@Configuration
	static class MultipleEndpointsConfiguration {

		@Bean
		public TestEndpoint testEndpoint() {
			return new TestEndpoint();
		}

		@Bean
		public NonWebEndpoint nonWebEndpoint() {
			return new NonWebEndpoint();
		}

	}

	@Configuration
	static class TestWebEndpointExtensionConfiguration {

		@Bean
		public TestWebEndpointExtension endpointExtension() {
			return new TestWebEndpointExtension();
		}

	}

	@Configuration
	static class ClashingOperationsEndpointConfiguration {

		@Bean
		public ClashingOperationsEndpoint clashingOperationsEndpoint() {
			return new ClashingOperationsEndpoint();
		}

	}

	@Configuration
	static class ClashingOperationsWebEndpointExtensionConfiguration {

		@Bean
		public ClashingOperationsWebEndpointExtension clashingOperationsExtension() {
			return new ClashingOperationsWebEndpointExtension();
		}

	}

	@Configuration
	@Import(TestEndpointConfiguration.class)
	static class OverriddenOperationWebEndpointExtensionConfiguration {

		@Bean
		public OverriddenOperationWebEndpointExtension overriddenOperationExtension() {
			return new OverriddenOperationWebEndpointExtension();
		}

	}

	@Configuration
	@Import(TestEndpointConfiguration.class)
	static class AdditionalOperationWebEndpointConfiguration {

		@Bean
		public AdditionalOperationWebEndpointExtension additionalOperationExtension() {
			return new AdditionalOperationWebEndpointExtension();
		}

	}

	@Configuration
	static class TestEndpointConfiguration {

		@Bean
		public TestEndpoint testEndpoint() {
			return new TestEndpoint();
		}

	}

	@Configuration
	static class ClashingWebEndpointConfiguration {

		@Bean
		public TestEndpoint testEndpoint() {
			return new TestEndpoint();
		}

		@Bean
		public TestWebEndpointExtension testExtensionOne() {
			return new TestWebEndpointExtension();
		}

		@Bean
		public TestWebEndpointExtension testExtensionTwo() {
			return new TestWebEndpointExtension();
		}

	}

	@Configuration
	static class ClashingStandardEndpointConfiguration {

		@Bean
		public TestEndpoint testEndpointTwo() {
			return new TestEndpoint();
		}

		@Bean
		public TestEndpoint testEndpointOne() {
			return new TestEndpoint();
		}

	}

	@Configuration
	static class ClashingSelectorsWebEndpointExtensionConfiguration {

		@Bean
		public TestEndpoint testEndpoint() {
			return new TestEndpoint();
		}

		@Bean
		public ClashingSelectorsWebEndpointExtension clashingSelectorsExtension() {
			return new ClashingSelectorsWebEndpointExtension();
		}

	}

	@Configuration
	static class InvalidWebExtensionConfiguration {

		@Bean
		public NonWebEndpoint nonWebEndpoint() {
			return new NonWebEndpoint();
		}

		@Bean
		public NonWebWebEndpointExtension nonWebWebEndpointExtension() {
			return new NonWebWebEndpointExtension();
		}

	}

	private static final class RequestPredicateMatcher {

		private final String path;

		private WebEndpointHttpMethod httpMethod;

		private RequestPredicateMatcher(String path) {
			this.path = path;
		}

		private RequestPredicateMatcher httpMethod(WebEndpointHttpMethod httpMethod) {
			this.httpMethod = httpMethod;
			return this;
		}

		private boolean matches(OperationRequestPredicate predicate) {
			return (this.path == null || this.path.equals(predicate.getPath()))
					&& (this.httpMethod == null
							|| this.httpMethod == predicate.getHttpMethod());
		}

		@Override
		public String toString() {
			return "Request predicate with path = '" + this.path + "', httpMethod = '"
					+ this.httpMethod + "'";
		}

	}

}

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

package org.springframework.boot.test.web.client;

import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URI;
import java.util.List;

import org.apache.http.client.config.RequestConfig;
import org.junit.Test;

import org.springframework.boot.test.web.client.TestRestTemplate.CustomHttpComponentsClientHttpRequestFactory;
import org.springframework.boot.test.web.client.TestRestTemplate.HttpClientOption;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.RequestEntity;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.http.client.InterceptingClientHttpRequestFactory;
import org.springframework.http.client.support.BasicAuthorizationInterceptor;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.mock.http.client.MockClientHttpResponse;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.ReflectionUtils.MethodCallback;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestOperations;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.DefaultUriTemplateHandler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Tests for {@link TestRestTemplate}.
 *
 * @author Dave Syer
 * @author Phillip Webb
 * @author Stephane Nicoll
 * @author Andy Wilkinson
 */
public class TestRestTemplateTests {

	@Test
	public void fromRestTemplateBuilder() {
		RestTemplateBuilder builder = mock(RestTemplateBuilder.class);
		RestTemplate delegate = new RestTemplate();
		given(builder.build()).willReturn(delegate);
		assertThat(new TestRestTemplate(builder).getRestTemplate()).isEqualTo(delegate);
	}

	@Test
	public void fromRestTemplateBuilderWithNoOptionsUsesCustomRequestFactory() {
		ClientHttpRequestFactory requestFactory = mock(ClientHttpRequestFactory.class);
		RestTemplateBuilder builder = new RestTemplateBuilder()
				.requestFactory(requestFactory);
		assertThat(new TestRestTemplate(builder).getRestTemplate().getRequestFactory())
				.isSameAs(requestFactory);
	}

	@Test
	public void simple() {
		// The Apache client is on the classpath so we get the fully-fledged factory
		ClientHttpRequestFactory requestFactory = new TestRestTemplate().getRestTemplate()
				.getRequestFactory();
		assertThat(requestFactory)
				.isInstanceOf(HttpComponentsClientHttpRequestFactory.class);
		assertThat(requestFactory)
				.isNotInstanceOf(CustomHttpComponentsClientHttpRequestFactory.class);
	}

	@Test
	public void authenticated() {
		assertThat(new TestRestTemplate("user", "password").getRestTemplate()
				.getRequestFactory())
						.isInstanceOf(InterceptingClientHttpRequestFactory.class);
	}

	@Test
	public void options() throws Exception {
		TestRestTemplate template = new TestRestTemplate(
				HttpClientOption.ENABLE_REDIRECTS);
		CustomHttpComponentsClientHttpRequestFactory factory = (CustomHttpComponentsClientHttpRequestFactory) template
				.getRestTemplate().getRequestFactory();
		RequestConfig config = factory.getRequestConfig();
		assertThat(config.isRedirectsEnabled()).isTrue();
	}

	@Test
	public void restOperationsAreAvailable() throws Exception {
		RestTemplate delegate = mock(RestTemplate.class);
		given(delegate.getUriTemplateHandler())
				.willReturn(new DefaultUriTemplateHandler());
		final TestRestTemplate restTemplate = new TestRestTemplate(delegate);
		ReflectionUtils.doWithMethods(RestOperations.class, new MethodCallback() {

			@Override
			public void doWith(Method method)
					throws IllegalArgumentException, IllegalAccessException {
				Method equivalent = ReflectionUtils.findMethod(TestRestTemplate.class,
						method.getName(), method.getParameterTypes());
				assertThat(equivalent).as("Method %s not found", method).isNotNull();
				assertThat(Modifier.isPublic(equivalent.getModifiers()))
						.as("Method %s should have been public", equivalent).isTrue();
				try {
					equivalent.invoke(restTemplate,
							mockArguments(method.getParameterTypes()));
				}
				catch (Exception ex) {
					throw new IllegalStateException(ex);
				}
			}

			private Object[] mockArguments(Class<?>[] parameterTypes) throws Exception {
				Object[] arguments = new Object[parameterTypes.length];
				for (int i = 0; i < parameterTypes.length; i++) {
					arguments[i] = mockArgument(parameterTypes[i]);
				}
				return arguments;
			}

			@SuppressWarnings("rawtypes")
			private Object mockArgument(Class<?> type) throws Exception {
				if (String.class.equals(type)) {
					return "String";
				}
				if (Object[].class.equals(type)) {
					return new Object[0];
				}
				if (URI.class.equals(type)) {
					return new URI("http://localhost");
				}
				if (HttpMethod.class.equals(type)) {
					return HttpMethod.GET;
				}
				if (Class.class.equals(type)) {
					return Object.class;
				}
				if (RequestEntity.class.equals(type)) {
					return new RequestEntity(HttpMethod.GET, new URI("http://localhost"));
				}
				return mock(type);
			}

		}, new ReflectionUtils.MethodFilter() {
			@Override
			public boolean matches(Method method) {
				return Modifier.isPublic(method.getModifiers());
			}

		});

	}

	@Test
	public void withBasicAuthAddsBasicAuthInterceptorWhenNotAlreadyPresent() {
		TestRestTemplate originalTemplate = new TestRestTemplate();
		TestRestTemplate basicAuthTemplate = originalTemplate.withBasicAuth("user",
				"password");
		assertThat(basicAuthTemplate.getRestTemplate().getMessageConverters())
				.containsExactlyElementsOf(
						originalTemplate.getRestTemplate().getMessageConverters());
		assertThat(basicAuthTemplate.getRestTemplate().getRequestFactory())
				.isInstanceOf(InterceptingClientHttpRequestFactory.class);
		assertThat(ReflectionTestUtils.getField(
				basicAuthTemplate.getRestTemplate().getRequestFactory(),
				"requestFactory"))
						.isInstanceOf(HttpComponentsClientHttpRequestFactory.class);
		assertThat(basicAuthTemplate.getRestTemplate().getUriTemplateHandler())
				.isSameAs(originalTemplate.getRestTemplate().getUriTemplateHandler());
		assertThat(basicAuthTemplate.getRestTemplate().getInterceptors()).hasSize(1);
		assertBasicAuthorizationInterceptorCredentials(basicAuthTemplate, "user",
				"password");
	}

	@Test
	public void withBasicAuthReplacesBasicAuthInterceptorWhenAlreadyPresent() {
		TestRestTemplate original = new TestRestTemplate("foo", "bar")
				.withBasicAuth("replace", "replace");
		TestRestTemplate basicAuth = original.withBasicAuth("user", "password");
		assertThat(basicAuth.getRestTemplate().getMessageConverters())
				.containsExactlyElementsOf(
						original.getRestTemplate().getMessageConverters());
		assertThat(basicAuth.getRestTemplate().getRequestFactory())
				.isInstanceOf(InterceptingClientHttpRequestFactory.class);
		assertThat(ReflectionTestUtils.getField(
				basicAuth.getRestTemplate().getRequestFactory(), "requestFactory"))
						.isInstanceOf(InterceptingClientHttpRequestFactory.class);
		assertThat(basicAuth.getRestTemplate().getUriTemplateHandler())
				.isSameAs(original.getRestTemplate().getUriTemplateHandler());
		assertThat(basicAuth.getRestTemplate().getInterceptors()).hasSize(1);
		assertBasicAuthorizationInterceptorCredentials(basicAuth, "user", "password");
	}

	@Test
	public void withBasicAuthDoesNotResetErrorHandler() throws Exception {
		TestRestTemplate originalTemplate = new TestRestTemplate("foo", "bar");
		ResponseErrorHandler errorHandler = mock(ResponseErrorHandler.class);
		originalTemplate.getRestTemplate().setErrorHandler(errorHandler);
		TestRestTemplate basicAuthTemplate = originalTemplate.withBasicAuth("user",
				"password");
		assertThat(basicAuthTemplate.getRestTemplate().getErrorHandler())
				.isSameAs(errorHandler);
	}

	@Test
	public void deleteHandlesRelativeUris() throws IOException {
		verifyRelativeUriHandling(new TestRestTemplateCallback() {

			@Override
			public void doWithTestRestTemplate(TestRestTemplate testRestTemplate,
					URI relativeUri) {
				testRestTemplate.delete(relativeUri);
			}

		});
	}

	@Test
	public void exchangeWithRequestEntityAndClassHandlesRelativeUris()
			throws IOException {
		verifyRelativeUriHandling(new TestRestTemplateCallback() {

			@Override
			public void doWithTestRestTemplate(TestRestTemplate testRestTemplate,
					URI relativeUri) {
				testRestTemplate.exchange(
						new RequestEntity<String>(HttpMethod.GET, relativeUri),
						String.class);
			}

		});
	}

	@Test
	public void exchangeWithRequestEntityAndParameterizedTypeReferenceHandlesRelativeUris()
			throws IOException {
		verifyRelativeUriHandling(new TestRestTemplateCallback() {

			@Override
			public void doWithTestRestTemplate(TestRestTemplate testRestTemplate,
					URI relativeUri) {
				testRestTemplate.exchange(
						new RequestEntity<String>(HttpMethod.GET, relativeUri),
						new ParameterizedTypeReference<String>() {
				});
			}

		});
	}

	@Test
	public void exchangeHandlesRelativeUris() throws IOException {
		verifyRelativeUriHandling(new TestRestTemplateCallback() {

			@Override
			public void doWithTestRestTemplate(TestRestTemplate testRestTemplate,
					URI relativeUri) {
				testRestTemplate.exchange(relativeUri, HttpMethod.GET,
						new HttpEntity<byte[]>(new byte[0]), String.class);
			}

		});
	}

	@Test
	public void exchangeWithParameterizedTypeReferenceHandlesRelativeUris()
			throws IOException {
		verifyRelativeUriHandling(new TestRestTemplateCallback() {

			@Override
			public void doWithTestRestTemplate(TestRestTemplate testRestTemplate,
					URI relativeUri) {
				testRestTemplate.exchange(relativeUri, HttpMethod.GET,
						new HttpEntity<byte[]>(new byte[0]),
						new ParameterizedTypeReference<String>() {
				});
			}

		});
	}

	@Test
	public void executeHandlesRelativeUris() throws IOException {
		verifyRelativeUriHandling(new TestRestTemplateCallback() {

			@Override
			public void doWithTestRestTemplate(TestRestTemplate testRestTemplate,
					URI relativeUri) {
				testRestTemplate.execute(relativeUri, HttpMethod.GET, null, null);
			}

		});
	}

	@Test
	public void getForEntityHandlesRelativeUris() throws IOException {
		verifyRelativeUriHandling(new TestRestTemplateCallback() {

			@Override
			public void doWithTestRestTemplate(TestRestTemplate testRestTemplate,
					URI relativeUri) {
				testRestTemplate.getForEntity(relativeUri, String.class);
			}

		});
	}

	@Test
	public void getForObjectHandlesRelativeUris() throws IOException {
		verifyRelativeUriHandling(new TestRestTemplateCallback() {

			@Override
			public void doWithTestRestTemplate(TestRestTemplate testRestTemplate,
					URI relativeUri) {
				testRestTemplate.getForObject(relativeUri, String.class);
			}

		});
	}

	@Test
	public void headForHeadersHandlesRelativeUris() throws IOException {
		verifyRelativeUriHandling(new TestRestTemplateCallback() {

			@Override
			public void doWithTestRestTemplate(TestRestTemplate testRestTemplate,
					URI relativeUri) {
				testRestTemplate.headForHeaders(relativeUri);
			}

		});
	}

	@Test
	public void optionsForAllowHandlesRelativeUris() throws IOException {
		verifyRelativeUriHandling(new TestRestTemplateCallback() {

			@Override
			public void doWithTestRestTemplate(TestRestTemplate testRestTemplate,
					URI relativeUri) {
				testRestTemplate.optionsForAllow(relativeUri);
			}

		});
	}

	@Test
	public void patchForObjectHandlesRelativeUris() throws IOException {
		verifyRelativeUriHandling(new TestRestTemplateCallback() {

			@Override
			public void doWithTestRestTemplate(TestRestTemplate testRestTemplate,
					URI relativeUri) {
				testRestTemplate.patchForObject(relativeUri, "hello", String.class);
			}

		});
	}

	@Test
	public void postForEntityHandlesRelativeUris() throws IOException {
		verifyRelativeUriHandling(new TestRestTemplateCallback() {

			@Override
			public void doWithTestRestTemplate(TestRestTemplate testRestTemplate,
					URI relativeUri) {
				testRestTemplate.postForEntity(relativeUri, "hello", String.class);
			}

		});
	}

	@Test
	public void postForLocationHandlesRelativeUris() throws IOException {
		verifyRelativeUriHandling(new TestRestTemplateCallback() {

			@Override
			public void doWithTestRestTemplate(TestRestTemplate testRestTemplate,
					URI relativeUri) {
				testRestTemplate.postForLocation(relativeUri, "hello");
			}

		});
	}

	@Test
	public void postForObjectHandlesRelativeUris() throws IOException {
		verifyRelativeUriHandling(new TestRestTemplateCallback() {

			@Override
			public void doWithTestRestTemplate(TestRestTemplate testRestTemplate,
					URI relativeUri) {
				testRestTemplate.postForObject(relativeUri, "hello", String.class);
			}

		});
	}

	@Test
	public void putHandlesRelativeUris() throws IOException {
		verifyRelativeUriHandling(new TestRestTemplateCallback() {

			@Override
			public void doWithTestRestTemplate(TestRestTemplate testRestTemplate,
					URI relativeUri) {
				testRestTemplate.put(relativeUri, "hello");
			}

		});
	}

	private void verifyRelativeUriHandling(TestRestTemplateCallback callback)
			throws IOException {
		ClientHttpRequestFactory requestFactory = mock(ClientHttpRequestFactory.class);
		MockClientHttpRequest request = new MockClientHttpRequest();
		request.setResponse(new MockClientHttpResponse(new byte[0], HttpStatus.OK));
		URI absoluteUri = URI
				.create("http://localhost:8080/a/b/c.txt?param=%7Bsomething%7D");
		given(requestFactory.createRequest(eq(absoluteUri), (HttpMethod) any()))
				.willReturn(request);
		RestTemplate delegate = new RestTemplate();
		TestRestTemplate template = new TestRestTemplate(delegate);
		delegate.setRequestFactory(requestFactory);
		LocalHostUriTemplateHandler uriTemplateHandler = new LocalHostUriTemplateHandler(
				new MockEnvironment());
		template.setUriTemplateHandler(uriTemplateHandler);
		callback.doWithTestRestTemplate(template,
				URI.create("/a/b/c.txt?param=%7Bsomething%7D"));
		verify(requestFactory).createRequest(eq(absoluteUri), (HttpMethod) any());
	}

	private void assertBasicAuthorizationInterceptorCredentials(
			TestRestTemplate testRestTemplate, String username, String password) {
		@SuppressWarnings("unchecked")
		List<ClientHttpRequestInterceptor> requestFactoryInterceptors = (List<ClientHttpRequestInterceptor>) ReflectionTestUtils
				.getField(testRestTemplate.getRestTemplate().getRequestFactory(),
						"interceptors");
		assertThat(requestFactoryInterceptors).hasSize(1);
		ClientHttpRequestInterceptor interceptor = requestFactoryInterceptors.get(0);
		assertThat(interceptor).isInstanceOf(BasicAuthorizationInterceptor.class);
		assertThat(ReflectionTestUtils.getField(interceptor, "username"))
				.isEqualTo(username);
		assertThat(ReflectionTestUtils.getField(interceptor, "password"))
				.isEqualTo(password);

	}

	private interface TestRestTemplateCallback {

		void doWithTestRestTemplate(TestRestTemplate testRestTemplate, URI relativeUri);

	}

}

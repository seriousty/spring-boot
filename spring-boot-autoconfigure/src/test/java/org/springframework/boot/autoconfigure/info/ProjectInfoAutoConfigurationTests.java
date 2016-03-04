/*
 * Copyright 2012-2016 the original author or authors.
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

package org.springframework.boot.autoconfigure.info;

import java.util.Map;

import org.junit.After;
import org.junit.Test;

import org.springframework.boot.autoconfigure.PropertyPlaceholderAutoConfiguration;
import org.springframework.boot.test.EnvironmentTestUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link ProjectInfoAutoConfiguration}.
 *
 * @author Stephane Nicoll
 */
public class ProjectInfoAutoConfigurationTests {

	private AnnotationConfigApplicationContext context;

	@After
	public void close() {
		if (this.context != null) {
			this.context.close();
		}
	}

	@Test
	public void gitInfoUnavailableIfResourceNotAvailable() {
		load();
		Map<String, GitInfo> beans = this.context.getBeansOfType(GitInfo.class);
		assertThat(beans).hasSize(0);
	}

	@Test
	public void gitLocationTakesPrecedenceOverLegacyKey() {
		load("spring.info.git.location=classpath:/org/springframework/boot/autoconfigure/info/git.properties",
				"spring.git.properties=classpath:/org/springframework/boot/autoconfigure/info/git-no-data.properties");
		GitInfo gitInfo = this.context.getBean(GitInfo.class);
		assertThat(gitInfo.getBranch()).isNull();
		assertThat(gitInfo.getCommit().getId()).isEqualTo("f95038e");
		assertThat(gitInfo.getCommit().getTime()).isEqualTo("2016-03-03T10:02:00");
	}

	@Test
	public void gitLegacyKeyIsUsedAsFallback() {
		load("spring.git.properties=classpath:/org/springframework/boot/autoconfigure/info/git-epoch.properties");
		GitInfo gitInfo = this.context.getBean(GitInfo.class);
		assertThat(gitInfo.getBranch()).isEqualTo("master");
		assertThat(gitInfo.getCommit().getId()).isEqualTo("5009933");
		assertThat(gitInfo.getCommit().getTime()).isEqualTo("2016-03-04T16:04:10");
	}

	@Test
	public void gitInfoWithNoData() {
		load("spring.info.git.location=classpath:/org/springframework/boot/autoconfigure/info/git-no-data.properties");
		GitInfo gitInfo = this.context.getBean(GitInfo.class);
		assertThat(gitInfo.getBranch()).isNull();
	}

	@Test
	public void gitInfoFallbackWithGitInfoBean() {
		load(CustomProjectInfoConfiguration.class,
				"spring.info.git.location=classpath:/org/springframework/boot/autoconfigure/info/git.properties");
		GitInfo gitInfo = this.context.getBean(GitInfo.class);
		assertThat(gitInfo).isSameAs(this.context.getBean("customGitInfo"));
	}

	@Test
	public void buildInfoDefaultLocation() {
		load();
		BuildInfo buildInfo = this.context.getBean(BuildInfo.class);
		assertThat(buildInfo.getGroup()).isEqualTo("com.example");
		assertThat(buildInfo.getArtifact()).isEqualTo("demo");
		assertThat(buildInfo.getVersion()).isEqualTo("0.0.1-SNAPSHOT");
		assertThat(buildInfo.getTimestamp()).isEqualTo("2016-03-04T11:02:00+0100");
	}

	@Test
	public void buildInfoCustomLocation() {
		load("spring.info.build.location=classpath:/org/springframework/boot/autoconfigure/info/build.properties");
		BuildInfo buildInfo = this.context.getBean(BuildInfo.class);
		assertThat(buildInfo.getGroup()).isEqualTo("com.example.acme");
		assertThat(buildInfo.getArtifact()).isEqualTo("acme");
		assertThat(buildInfo.getVersion()).isEqualTo("1.0.1-SNAPSHOT");
		assertThat(buildInfo.getTimestamp()).isEqualTo("2016-03-04T11:42:00+0100");
	}

	@Test
	public void buildInfoCustomInvalidLocation() {
		load("spring.info.build.location=classpath:/org/acme/no-build.properties");
		Map<String, BuildInfo> beans = this.context.getBeansOfType(BuildInfo.class);
		assertThat(beans).hasSize(0);
	}

	@Test
	public void buildInfoFallbackWithBuildInfoBean() {
		load(CustomProjectInfoConfiguration.class);
		BuildInfo buildInfo = this.context.getBean(BuildInfo.class);
		assertThat(buildInfo).isSameAs(this.context.getBean("customBuildInfo"));
	}

	private void assertGitInfo(GitInfo gitInfo) {
		assertThat(gitInfo.getBranch()).isNull();
		assertThat(gitInfo.getCommit().getId()).isEqualTo("f95038e");
		assertThat(gitInfo.getCommit().getTime()).isEqualTo("2016-03-03T10:02:00+0100");
	}

	private void load(String... environment) {
		load(null, environment);
	}

	private void load(Class<?> config, String... environment) {
		AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
		if (config != null) {
			context.register(config);
		}
		context.register(PropertyPlaceholderAutoConfiguration.class,
				ProjectInfoAutoConfiguration.class);
		EnvironmentTestUtils.addEnvironment(context, environment);
		context.refresh();
		this.context = context;
	}

	@Configuration
	static class CustomProjectInfoConfiguration {

		@Bean
		public GitInfo customGitInfo() {
			return new GitInfo();
		}

		@Bean
		public BuildInfo customBuildInfo() {
			return new BuildInfo();
		}

	}

}

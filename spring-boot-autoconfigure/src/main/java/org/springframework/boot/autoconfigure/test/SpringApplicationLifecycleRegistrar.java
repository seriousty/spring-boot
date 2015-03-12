/*
 * Copyright 2012-2015 the original author or authors.
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

package org.springframework.boot.autoconfigure.test;

import java.lang.management.ManagementFactory;
import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.util.Assert;

/**
 * Register a {@link SpringApplicationLifecycleMXBean} implementation so that it is only exposed
 * to JMX.
 *
 * @author Stephane Nicoll
 * @since 1.3.0
 */
class SpringApplicationLifecycleRegistrar implements ApplicationContextAware, InitializingBean, DisposableBean,
		ApplicationListener<ApplicationStartedEvent> {

	private static final Log logger = LogFactory.getLog(SpringApplicationLifecycleRegistrar.class);

	private ConfigurableApplicationContext applicationContext;

	private final ObjectName objectName;

	private boolean ready = false;

	public SpringApplicationLifecycleRegistrar(String name) throws MalformedObjectNameException {
		this.objectName = new ObjectName(name);
	}

	@Override
	public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
		Assert.isTrue(applicationContext instanceof ConfigurableApplicationContext,
				"ApplicationContext does not implement ConfigurableApplicationContext");
		this.applicationContext = (ConfigurableApplicationContext) applicationContext;
	}

	@Override
	public void onApplicationEvent(ApplicationStartedEvent event) {
		ready = true;
	}

	@Override
	public void afterPropertiesSet() throws Exception {
		MBeanServer server = ManagementFactory.getPlatformMBeanServer();
		server.registerMBean(new SpringApplicationLifecycle(), objectName);
		if (logger.isDebugEnabled()) {
			logger.debug("Application shutdown registered with name '" + objectName + "'");
		}
	}

	@Override
	public void destroy() throws Exception {
		ManagementFactory.getPlatformMBeanServer().unregisterMBean(objectName);
	}

	private class SpringApplicationLifecycle implements SpringApplicationLifecycleMXBean {

		@Override
		public boolean isReady() {
			return ready;
		}

		@Override
		public void shutdown() {
			logger.info("Application shutdown requested.");
			applicationContext.close();
		}
	}

}


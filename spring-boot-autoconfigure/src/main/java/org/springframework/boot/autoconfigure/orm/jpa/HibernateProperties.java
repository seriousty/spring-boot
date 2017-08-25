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

package org.springframework.boot.autoconfigure.orm.jpa;

import java.util.HashMap;
import java.util.Map;

import javax.sql.DataSource;

import org.springframework.boot.jdbc.EmbeddedDatabaseConnection;
import org.springframework.util.StringUtils;

/**
 * Configuration properties for Hibernate.
 * @author Stephane Nicoll
 * @since 2.0.0
 */
public class HibernateProperties {

	private static final String USE_NEW_ID_GENERATOR_MAPPINGS = "hibernate.id."
			+ "new_generator_mappings";

	/**
	 * DDL mode. This is actually a shortcut for the "hibernate.hbm2ddl.auto"
	 * property. Default to "create-drop" when using an embedded database, "none"
	 * otherwise.
	 */
	private String ddlAuto;

	/**
	 * Use Hibernate's newer IdentifierGenerator for AUTO, TABLE and SEQUENCE. This is
	 * actually a shortcut for the "hibernate.id.new_generator_mappings" property.
	 * When not specified will default to "false" for backwards compatibility.
	 */
	private Boolean useNewIdGeneratorMappings;

	private final Naming naming = new Naming();

	public String getDdlAuto() {
		return this.ddlAuto;
	}

	public void setDdlAuto(String ddlAuto) {
		this.ddlAuto = ddlAuto;
	}

	public Boolean isUseNewIdGeneratorMappings() {
		return this.useNewIdGeneratorMappings;
	}

	public void setUseNewIdGeneratorMappings(Boolean useNewIdGeneratorMappings) {
		this.useNewIdGeneratorMappings = useNewIdGeneratorMappings;
	}

	public Naming getNaming() {
		return this.naming;
	}

	/**
	 * Get configuration properties for the initialization of the main Hibernate
	 * EntityManagerFactory.
	 * @param dataSource the DataSource in case it is needed to determine the properties
	 * @return some Hibernate properties for configuration
	 */
	public Map<String, String> getHibernateProperties(Map<String,String> jpaProperties,
			DataSource dataSource) {
		Map<String, String> result = new HashMap<>(jpaProperties);
		applyNewIdGeneratorMappings(result);
		getNaming().applyNamingStrategies(result);
		String ddlAuto = getOrDeduceDdlAuto(jpaProperties, dataSource);
		if (StringUtils.hasText(ddlAuto) && !"none".equals(ddlAuto)) {
			result.put("hibernate.hbm2ddl.auto", ddlAuto);
		}
		else {
			result.remove("hibernate.hbm2ddl.auto");
		}
		return result;
	}

	private void applyNewIdGeneratorMappings(Map<String, String> result) {
		if (this.useNewIdGeneratorMappings != null) {
			result.put(USE_NEW_ID_GENERATOR_MAPPINGS,
					this.useNewIdGeneratorMappings.toString());
		}
		else if (!result.containsKey(USE_NEW_ID_GENERATOR_MAPPINGS)) {
			result.put(USE_NEW_ID_GENERATOR_MAPPINGS, "true");
		}
	}

	private String getOrDeduceDdlAuto(Map<String, String> existing,
			DataSource dataSource) {
		String ddlAuto = (this.ddlAuto != null ? this.ddlAuto
				: getDefaultDdlAuto(dataSource));
		if (!existing.containsKey("hibernate." + "hbm2ddl.auto")
				&& !"none".equals(ddlAuto)) {
			return ddlAuto;
		}
		if (existing.containsKey("hibernate." + "hbm2ddl.auto")) {
			return existing.get("hibernate.hbm2ddl.auto");
		}
		return "none";
	}

	private String getDefaultDdlAuto(DataSource dataSource) {
		if (EmbeddedDatabaseConnection.isEmbedded(dataSource)) {
			return "create-drop";
		}
		return "none";
	}


	public static class Naming {

		private static final String DEFAULT_PHYSICAL_STRATEGY = "org.springframework.boot.orm.jpa.hibernate.SpringPhysicalNamingStrategy";

		private static final String DEFAULT_IMPLICIT_STRATEGY = "org.springframework.boot.orm.jpa.hibernate.SpringImplicitNamingStrategy";

		/**
		 * Fully qualified name of the implicit naming strategy.
		 */
		private String implicitStrategy;

		/**
		 * Fully qualified name of the physical naming strategy.
		 */
		private String physicalStrategy;

		public String getImplicitStrategy() {
			return this.implicitStrategy;
		}

		public void setImplicitStrategy(String implicitStrategy) {
			this.implicitStrategy = implicitStrategy;
		}

		public String getPhysicalStrategy() {
			return this.physicalStrategy;
		}

		public void setPhysicalStrategy(String physicalStrategy) {
			this.physicalStrategy = physicalStrategy;
		}

		private void applyNamingStrategies(Map<String, String> properties) {
			applyNamingStrategy(properties, "hibernate.implicit_naming_strategy",
					this.implicitStrategy, DEFAULT_IMPLICIT_STRATEGY);
			applyNamingStrategy(properties, "hibernate.physical_naming_strategy",
					this.physicalStrategy, DEFAULT_PHYSICAL_STRATEGY);
		}

		private void applyNamingStrategy(Map<String, String> properties, String key,
				String strategy, String defaultStrategy) {
			if (strategy != null) {
				properties.put(key, strategy);
			}
			else if (defaultStrategy != null && !properties.containsKey(key)) {
				properties.put(key, defaultStrategy);
			}
		}

	}

}

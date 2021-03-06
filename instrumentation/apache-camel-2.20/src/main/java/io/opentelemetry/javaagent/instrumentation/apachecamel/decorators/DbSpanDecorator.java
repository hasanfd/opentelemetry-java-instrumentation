/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

// Includes work from:
/*
 * Apache Camel Opentracing Component
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license
 * agreements. See the NOTICE file distributed with this work for additional information regarding
 * copyright ownership. The ASF licenses this file to You under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 *
 * <p>Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.opentelemetry.javaagent.instrumentation.apachecamel.decorators;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.attributes.SemanticAttributes;
import io.opentelemetry.javaagent.instrumentation.apachecamel.CamelDirection;
import java.net.URI;
import java.util.Map;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;

class DbSpanDecorator extends BaseSpanDecorator {

  private final String component;
  private final String system;

  DbSpanDecorator(String component, String system) {
    this.component = component;
    this.system = system;
  }

  @Override
  public String getOperationName(
      Exchange exchange, Endpoint endpoint, CamelDirection camelDirection) {

    switch (component) {
      case "mongodb":
      case "elasticsearch":
        {
          Map<String, String> queryParameters = toQueryParameters(endpoint.getEndpointUri());
          if (queryParameters.containsKey("operation")) {
            return queryParameters.get("operation");
          }
        }
    }
    return super.getOperationName(exchange, endpoint, camelDirection);
  }

  private String getStatement(Exchange exchange, Endpoint endpoint) {
    switch (component) {
      case "mongodb":
        {
          Map<String, String> queryParameters = toQueryParameters(endpoint.getEndpointUri());
          return queryParameters.toString();
        }
      case "cql":
        {
          Object cql = exchange.getIn().getHeader("CamelCqlQuery");
          if (cql != null) {
            return cql.toString();
          } else {
            Map<String, String> queryParameters = toQueryParameters(endpoint.getEndpointUri());
            if (queryParameters.containsKey("cql")) {
              return queryParameters.get("cql");
            }
          }
        }
      case "jdbc":
        {
          Object body = exchange.getIn().getBody();
          if (body instanceof String) {
            return (String) body;
          }
        }
      case "sql":
        {
          Object sqlquery = exchange.getIn().getHeader("CamelSqlQuery");
          if (sqlquery instanceof String) {
            return (String) sqlquery;
          }
        }
    }
    return null;
  }

  private String getDbName(Endpoint endpoint) {
    switch (component) {
      case "mongodb":
        {
          Map<String, String> queryParameters = toQueryParameters(endpoint.getEndpointUri());
          return queryParameters.get("database");
        }
      case "cql":
        {
          URI uri = URI.create(endpoint.getEndpointUri());
          if (uri.getPath() != null && uri.getPath().length() > 0) {
            // Strip leading '/' from path
            return uri.getPath().substring(1);
          }
        }
      case "elasticsearch":
        {
          Map<String, String> queryParameters = toQueryParameters(endpoint.getEndpointUri());
          if (queryParameters.containsKey("indexName")) {
            return queryParameters.get("indexName");
          }
        }
    }
    return null;
  }

  @Override
  public void pre(Span span, Exchange exchange, Endpoint endpoint, CamelDirection camelDirection) {
    super.pre(span, exchange, endpoint, camelDirection);

    span.setAttribute(SemanticAttributes.DB_SYSTEM, system);
    String statement = getStatement(exchange, endpoint);
    if (statement != null) {
      span.setAttribute(SemanticAttributes.DB_STATEMENT, statement);
    }
    String dbName = getDbName(endpoint);
    if (dbName != null) {
      span.setAttribute(SemanticAttributes.DB_NAME, dbName);
    }
  }
}

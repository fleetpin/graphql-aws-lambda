/*
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package com.fleetpin.graphql.aws.lambda;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Collections;

import org.junit.jupiter.api.Test;

import com.fleetpin.graphql.builder.SchemaBuilder;

import graphql.ExecutionResult;

public class InterceptExecutionStrategyTest {

	@Test
	public void testExtractsName() throws ReflectiveOperationException {
		var graph = SchemaBuilder.build("com.fleetpin.graphql.aws.lambda.model").subscriptionExecutionStrategy(new InterceptExecutionStrategy()).build();

		GraphQLQuery query = new GraphQLQuery(null, "subscription example {allExamples{name}}", Collections.emptyMap())  ;
		ExecutionResult result = graph.execute(builder -> builder.query(query.getQuery()).operationName(query.getOperationName()).variables(query.getVariables()));
		String subscription = result.getData();
		assertEquals("allExamples", subscription);
		assertEquals(Collections.emptyList(), result.getErrors());
	}

}


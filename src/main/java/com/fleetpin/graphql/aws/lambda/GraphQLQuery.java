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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Collections;
import java.util.Map;

public class GraphQLQuery {
    private final String operationName;
    private final String query;
    private final Map<String, Object> variables;

    @JsonCreator
    public GraphQLQuery(
            @JsonProperty("operationName") final String operationName,
            @JsonProperty("query") final String query,
            @JsonProperty("variables") final Map<String, Object> variables
    ) {
        this.operationName = operationName;
        this.query = query;
        this.variables = variables;
    }

    public String getOperationName() {
        return operationName;
    }

    public String getQuery() {
        return query;
    }

    public Map<String, Object> getVariables() {
        if (variables == null) {
            return Collections.emptyMap();
        }
        return variables;
    }
}

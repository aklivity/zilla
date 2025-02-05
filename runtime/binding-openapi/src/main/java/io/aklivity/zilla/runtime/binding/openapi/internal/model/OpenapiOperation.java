/*
 * Copyright 2021-2024 Aklivity Inc
 *
 * Licensed under the Aklivity Community License (the "License"); you may not use
 * this file except in compliance with the License.  You may obtain a copy of the
 * License at
 *
 *   https://www.aklivity.io/aklivity-community-license/
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OF ANY KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations under the License.
 */
package io.aklivity.zilla.runtime.binding.openapi.internal.model;

import java.util.List;
import java.util.Map;

import jakarta.json.bind.annotation.JsonbProperty;

import io.aklivity.zilla.runtime.binding.openapi.internal.model.extensions.http.kafka.OpenapiHttpKafkaOperationExtension;

public class OpenapiOperation
{
    public String operationId;
    public List<OpenapiParameter> parameters;
    public OpenapiRequestBody requestBody;
    public Map<String, OpenapiResponse> responses;
    public List<Map<String, List<String>>> security;
    public List<OpenapiServer> servers;

    @JsonbProperty("x-zilla-http-kafka")
    public OpenapiHttpKafkaOperationExtension httpKafka;
}

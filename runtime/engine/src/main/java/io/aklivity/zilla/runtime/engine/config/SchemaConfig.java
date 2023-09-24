/*
 * Copyright 2021-2023 Aklivity Inc.
 *
 * Aklivity licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.aklivity.zilla.runtime.engine.config;

public class SchemaConfig
{
    public final String schema;
    public final String strategy;
    public final String version;
    public final String subject;
    public final int id;

    public SchemaConfig(
        String schema,
        String strategy,
        String subject,
        String version,
        int id)
    {
        this.schema = schema;
        this.strategy = strategy;
        this.version = version;
        this.subject = subject;
        this.id = id;
    }
}

/*
 * Copyright 2021-2026 Aklivity Inc
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
package io.aklivity.zilla.config.engine;

import static java.util.Objects.requireNonNull;

public abstract class ClassifierConfig extends NamedConfig
{
    public transient long embeddingId;
    public transient String qembedding;
    public transient long storeId;
    public transient String qstore;

    public final String namespace;
    public final String type;
    public final String embedding;
    public final String store;
    public final OptionsConfig options;

    protected ClassifierConfig(
        String namespace,
        String name,
        String type,
        String embedding,
        String store,
        OptionsConfig options)
    {
        super(name);
        this.namespace = requireNonNull(namespace);
        this.qname = String.format("%s:%s", namespace, name);
        this.type = requireNonNull(type);
        this.embedding = embedding;
        this.store = store;
        this.options = options;
    }
}

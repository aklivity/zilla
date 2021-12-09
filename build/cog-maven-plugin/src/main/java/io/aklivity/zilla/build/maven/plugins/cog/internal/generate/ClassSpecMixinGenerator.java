/*
 * Copyright 2021-2021 Aklivity Inc.
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
package io.aklivity.zilla.build.maven.plugins.cog.internal.generate;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.TypeSpec;

public abstract class ClassSpecMixinGenerator
{
    protected final ClassName thisType;
    protected final TypeSpec.Builder builder;

    protected ClassSpecMixinGenerator(
        ClassName thisType,
        TypeSpec.Builder builder)
    {
        this.thisType = thisType;
        this.builder = builder;
    }

    public TypeSpec.Builder build()
    {
        return builder;
    }
}

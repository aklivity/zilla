/*
 * Copyright 2021-2023 Aklivity Inc
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
package io.aklivity.zilla.runtime.command.generate.internal.airline;

import java.nio.file.Paths;

import com.github.rvesse.airline.model.ArgumentsMetadata;
import com.github.rvesse.airline.model.OptionMetadata;
import com.github.rvesse.airline.parser.ParseState;
import com.github.rvesse.airline.types.TypeConverter;
import com.github.rvesse.airline.types.TypeConverterProvider;
import com.github.rvesse.airline.types.numerics.NumericTypeConverter;

public final class ZillaConfigCommandPathConverterProvider implements TypeConverterProvider
{
    private final ZillaDumpCommandPathConverter converter = new ZillaDumpCommandPathConverter();

    private final class ZillaDumpCommandPathConverter implements TypeConverter
    {
        @Override
        public void setNumericConverter(
            NumericTypeConverter converter)
        {
        }

        @Override
        public Object convert(
            String name,
            Class<?> type,
            String value)
        {
            return Paths.get(value);
        }
    }

    @Override
    public <T> TypeConverter getTypeConverter(
        OptionMetadata option,
        ParseState<T> state)
    {
        return converter;
    }

    @Override
    public <T> TypeConverter getTypeConverter(
        ArgumentsMetadata arguments,
        ParseState<T> state)
    {
        return converter;
    }
}

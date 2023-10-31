/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.api.internal.provider;

import com.google.common.base.Preconditions;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.internal.Cast;

import javax.annotation.Nullable;

public class DefaultProperty<T> extends AbstractProperty<T, ProviderInternal<? extends T>> implements Property<T> {

    private static ThreadLocal<ValueProvenance> nextProvenance = new ThreadLocal<>();

    private final Class<T> type;
    private final ValueSanitizer<T> sanitizer;
    private ValueProvenance provenance;

    public DefaultProperty(PropertyHost propertyHost, Class<T> type) {
        super(propertyHost);
        this.type = type;
        this.sanitizer = ValueSanitizers.forType(type);
        init(Providers.notDefined());
    }

    public ValueProvenance getProvenance() {
        ProviderInternal<? extends T> provider = getProvider();
        if (provider instanceof DefaultProperty) {
            ValueProvenance upstreamProvenance = ((DefaultProperty<? extends T>) provider).getProvenance();
            if (upstreamProvenance != null) {
                return upstreamProvenance;
            }
        }
        return provenance;
    }

    @Nullable
    public static ValueProvenance getProvenance(Object value) {
        if (value instanceof DefaultProperty) {
            return ((DefaultProperty) value).getProvenance();
        }
        return null;
    }

    public static <T> T withProv(T value, String sourceUnit, int lineNumber, int columnNumber) {
        nextProvenance.set(new ValueProvenance() {
            @Override
            public Integer getColumn() {
                return columnNumber == -1 ? null : columnNumber;
            }

            @Override
            public Integer getLine() {
                return lineNumber == -1 ? null : lineNumber;
            }

            @Override
            public String getSourceUnit() {
                return sourceUnit;
            }

            @Override
            public String toString() {
                return String.format("%s:%d:%d", sourceUnit, getLine(), getColumn());
            }
        });
        return value;
    }

    @Override
    public Object unpackState() {
        return getProvider();
    }

    @Override
    public Class<?> publicType() {
        return Property.class;
    }

    @Override
    public int getFactoryId() {
        return ManagedFactories.PropertyManagedFactory.FACTORY_ID;
    }

    @Override
    public Class<T> getType() {
        return type;
    }

    @Override
    public void setFromAnyValue(Object object) {
        if (object instanceof Provider) {
            set(Cast.<Provider<T>>uncheckedNonnullCast(object));
        } else {
            set(Cast.<T>uncheckedNonnullCast(object));
        }
    }

    @Override
    public void set(@Nullable T value) {
        if (value == null) {
            discardValue();
        } else {
            setSupplier(Providers.fixedValue(getValidationDisplayName(), value, type, sanitizer));
        }
        ValueProvenance newProvenance = nextProvenance.get();
        provenance = newProvenance;
    }

    @Override
    public Property<T> value(@Nullable T value) {
        set(value);
        return this;
    }

    @Override
    public Property<T> value(Provider<? extends T> provider) {
        set(provider);
        return this;
    }

    public ProviderInternal<? extends T> getProvider() {
        return getSupplier();
    }

    public DefaultProperty<T> provider(Provider<? extends T> provider) {
        set(provider);
        return this;
    }

    @Override
    public void set(Provider<? extends T> provider) {
        Preconditions.checkArgument(provider != null, "Cannot set the value of a property using a null provider.");
        ProviderInternal<? extends T> p = Providers.internal(provider);
        setSupplier(p.asSupplier(getValidationDisplayName(), type, sanitizer));
    }

    @Override
    public Property<T> convention(@Nullable T value) {
        if (value == null) {
            setConvention(Providers.notDefined());
        } else {
            setConvention(Providers.fixedValue(getValidationDisplayName(), value, type, sanitizer));
        }
        return this;
    }

    @Override
    public Property<T> convention(Provider<? extends T> provider) {
        Preconditions.checkArgument(provider != null, "Cannot set the convention of a property using a null provider.");
        setConvention(Providers.internal(provider).asSupplier(getValidationDisplayName(), type, sanitizer));
        return this;
    }

    @Override
    protected ExecutionTimeValue<? extends T> calculateOwnExecutionTimeValue(ProviderInternal<? extends T> value) {
        // Discard this property from a provider chain, as it does not contribute anything to the calculation.
        return value.calculateExecutionTimeValue();
    }

    @Override
    protected Value<? extends T> calculateValueFrom(ProviderInternal<? extends T> value, ValueConsumer consumer) {
        return value.calculateValue(consumer);
    }

    @Override
    protected ProviderInternal<? extends T> finalValue(ProviderInternal<? extends T> value, ValueConsumer consumer) {
        return value.withFinalValue(consumer);
    }

    @Override
    protected String describeContents() {
        // NOTE: Do not realize the value of the Provider in toString().  The debugger will try to call this method and make debugging really frustrating.
        return String.format("property(%s, %s)", type.getName(), getSupplier());
    }
}

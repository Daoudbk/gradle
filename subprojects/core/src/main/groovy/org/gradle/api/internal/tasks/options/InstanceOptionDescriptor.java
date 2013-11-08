/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.api.internal.tasks.options;

import org.gradle.internal.reflect.JavaMethod;
import org.gradle.internal.reflect.JavaReflectionUtil;
import org.gradle.util.CollectionUtils;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class InstanceOptionDescriptor implements OptionDescriptor {

    private final Object object;
    private final OptionDescriptor delegate;

    public InstanceOptionDescriptor(Object object, OptionDescriptor delegate) {
        this.object = object;
        this.delegate = delegate;
    }

    public OptionElement getOptionElement() {
        return delegate.getOptionElement();
    }

    public String getName() {
        return delegate.getName();
    }

    public List<String> getAvailableValues() {
        final List<String> values = delegate.getAvailableValues();

        if (String.class.isAssignableFrom(getArgumentType())) {
            values.addAll(lookupDynamicAvailableValues());
        }
        return values;
    }

    public Class getArgumentType() {
        return delegate.getArgumentType();
    }

    private List<String> lookupDynamicAvailableValues() {
        List<String> dynamicAvailableValues = null;
        for (Class<?> type = object.getClass(); type != Object.class && type != null; type = type.getSuperclass()) {
            for (Method method : type.getDeclaredMethods()) {
                OptionValues optionValues = method.getAnnotation(OptionValues.class);
                if (optionValues != null){
                    if (Collection.class.isAssignableFrom(method.getReturnType()) && method.getParameterTypes().length == 0) {
                        if(CollectionUtils.toList(optionValues.value()).contains(getName())) {
                            if(dynamicAvailableValues==null){
                                final JavaMethod<Object, Collection> methodToInvoke = JavaReflectionUtil.method(Object.class, Collection.class, method);
                                Collection values = methodToInvoke.invoke(object);
                                dynamicAvailableValues = CollectionUtils.toStringList(values);
                            }else{
                                throw new OptionValidationException(
                                        String.format("OptionValues for %s cannot be attached to multiple methods in class %s.",
                                                getName(),
                                                type.getName()));
                            }
                        }
                    }else{
                        throw new OptionValidationException(
                                String.format("OptionValues annotation not supported on method %s in class %s. Supported method must return Collection and take no parameters",
                                        method.getName(),
                                        type.getName()));
                    }
                }
            }
        }
        return dynamicAvailableValues != null ? dynamicAvailableValues : Collections.<String>emptyList();
    }

    public String getDescription() {
        return delegate.getDescription();
    }

    public void apply(Object objectParam, List<String> parameterValues) {
        if (objectParam != object) {
            throw new AssertionError(String.format("Object %s not applyable. Expecting %s", objectParam, object));
        }
        delegate.apply(objectParam, parameterValues);
    }

    public int compareTo(OptionDescriptor o) {
        return delegate.compareTo(o);
    }
}
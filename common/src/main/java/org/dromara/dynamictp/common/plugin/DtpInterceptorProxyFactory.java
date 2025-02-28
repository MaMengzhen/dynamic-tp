/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.dromara.dynamictp.common.plugin;

import com.google.common.collect.Maps;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.implementation.MethodDelegation;
import net.bytebuddy.matcher.ElementMatchers;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * @author windsearcher.lq
 * @since 1.1.4
 */
public class DtpInterceptorProxyFactory {

    private DtpInterceptorProxyFactory() { }

    public static Object enhance(Object target, DtpInterceptor interceptor) {
        return enhance(target, null, null, interceptor);
    }

    public static Object enhance(Object target, Class<?>[] argumentTypes, Object[] arguments, DtpInterceptor interceptor) {
        Map<Class<?>, Set<Method>> signatureMap = getSignatureMap(interceptor);
        if (!signatureMap.containsKey(target.getClass())) {
            return target;
        }

        try {
            // 使用 Byte Buddy 创建代理类
            Class<?> dynamicType = new ByteBuddy()
                    .subclass(target.getClass())
                    .method(ElementMatchers.isPublic()) // 匹配所有公共方法
                    .intercept(MethodDelegation.to(new DtpInterceptorProxy(target, interceptor, signatureMap)))
                    .make()
                    .load(target.getClass().getClassLoader())
                    .getLoaded();

            // 创建代理实例
            if (Objects.isNull(argumentTypes) || Objects.isNull(arguments)) {
                return dynamicType.getDeclaredConstructor().newInstance();
            } else {
                return dynamicType.getDeclaredConstructor(argumentTypes).newInstance(arguments);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to create proxy", e);
        }
    }

    private static Map<Class<?>, Set<Method>> getSignatureMap(DtpInterceptor interceptor) {
        DtpIntercepts interceptsAnno = interceptor.getClass().getAnnotation(DtpIntercepts.class);
        if (interceptsAnno == null) {
            throw new PluginException("No @DtpIntercepts annotation was found in interceptor " + interceptor.getClass().getName());
        }

        DtpSignature[] signatures = interceptsAnno.signatures();
        Map<Class<?>, Set<Method>> signatureMap = Maps.newHashMap();
        for (DtpSignature signature : signatures) {
            Set<Method> methods = signatureMap.computeIfAbsent(signature.clazz(), k -> new HashSet<>());
            try {
                Method method = signature.clazz().getMethod(signature.method(), signature.args());
                methods.add(method);
            } catch (NoSuchMethodException e) {
                throw new PluginException("Could not find method on " + signature.clazz() + " named " + signature.method() + ". Cause: " + e, e);
            }
        }
        return signatureMap;
    }
}

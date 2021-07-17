/*
 * Copyright 2021 kailuo
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.github.javahello.proxy.core;

import com.github.javahello.proxy.conf.ProxyConf;

import java.util.*;

/**
 * @author kailuo
 */
public class ProxyContext {
    private ProxyConf proxyConf;

    private ProxyContext() {}
    public static ProxyContext create(ProxyConf proxyConf) {
        ProxyContext proxyContext = new ProxyContext();
        proxyContext.proxyConf = proxyConf;
        return proxyContext;
    }

    /**
     * 使用域名查询后端服务
     * @param hostname
     * @return
     */
    public Optional<List<String>> findUpstream(String hostname) {
        ProxyConf.Upstream upstream = proxyConf.getUpstream().get(hostname);
        return Optional.ofNullable(upstream).map(ProxyConf.Upstream::getServer);
    }
}

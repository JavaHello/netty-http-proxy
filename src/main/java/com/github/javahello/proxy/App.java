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
package com.github.javahello.proxy;

import com.github.javahello.proxy.conf.ProxyConf;
import com.github.javahello.proxy.core.ProxyContext;
import com.github.javahello.proxy.core.ProxyServer;
import com.github.javahello.proxy.util.ClassPathHelper;
import com.github.javahello.proxy.util.YmlHelper;

public class App {
    public static void main(String[] args) throws Exception {
        String config = ClassPathHelper.readClasspathFile("/proxy.yml");
        ProxyConf proxyConf = YmlHelper.reSerializer(config, ProxyConf.class);
        ProxyContext proxyContext = ProxyContext.create(proxyConf);
        for (ProxyConf.Server server : proxyConf.getServers()) {
            ProxyServer.create(server, proxyContext);
        }
    }
}
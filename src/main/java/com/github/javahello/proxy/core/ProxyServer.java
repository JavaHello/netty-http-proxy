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
import com.github.javahello.proxy.conf.UpstreamServer;
import com.github.javahello.proxy.util.ProxyClientHelper;
import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.*;

import java.io.Closeable;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class ProxyServer implements Closeable {
    final EventLoopGroup bossGroup;
    final EventLoopGroup workerGroup;
    final ServerBootstrap serverBootstrap = new ServerBootstrap();
    final Bootstrap clinetBootstrap = new Bootstrap();

    private Channel sc;
    private ProxyConf.Server serverConf;
    private ProxyContext proxyContext;

    private List<UrlMatch> urlMatches = new ArrayList<>();

    class UrlMatch implements Comparable<UrlMatch> {
        private String api;
        private ProxyConf.Location location;
        private String hostname;

        @Override
        public int compareTo(UrlMatch o) {
            String c2 = Optional.ofNullable(o)
                    .map(UrlMatch::getApi)
                    .orElse(null);
            if (c2 == null) {
                return -1;
            }
            if (this.api == null) {
                return 0;
            }
            if (this.api.length() > c2.length()) {
                return 1;
            }
            return 0;
        }

        public boolean match(String uri) {
            return Optional.ofNullable(uri)
                    .map(e -> e.split("/"))
                    .map(e -> {
                        if (e.length == 0) {
                            return "/";
                        }
                        String r = "/";
                        if (e.length > 1) {
                            r += e[1];
                        } else {
                            r += e[0];
                        }
                        return r;
                    })
                    .map(e -> e.equalsIgnoreCase(api)).orElse(false);
        }

        public String getApi() {
            return api;
        }

        public void setApi(String api) {
            this.api = api;
        }

        public ProxyConf.Location getLocation() {
            return location;
        }

        public void setLocation(ProxyConf.Location location) {
            this.location = location;
        }

        public String getHostname() {
            return hostname;
        }

        public void setHostname(String hostname) {
            this.hostname = hostname;
        }
    }

    public ProxyServer() {
        bossGroup = new NioEventLoopGroup();
        workerGroup = new NioEventLoopGroup();
        serverBootstrap.group(bossGroup, workerGroup).channel(NioServerSocketChannel.class);
    }

    public void initProxyCline() throws MalformedURLException {
        for (Map.Entry<String, ProxyConf.Location> locationEntry : serverConf.getLocation().entrySet()) {
            UrlMatch urlMatch = new UrlMatch();
            urlMatch.setApi(locationEntry.getKey());
            urlMatch.setLocation(locationEntry.getValue());
            URL url = new URL(urlMatch.getLocation().getProxyPass());
            if (proxyContext.hasUpstream(url.getHost())) {
                urlMatch.setHostname(url.getHost());
            } else {
                UpstreamServer upstreamServer = ProxyClientHelper.urlToUps(url);
                urlMatch.setHostname(upstreamServer.toKey());
            }
            urlMatches.add(urlMatch);
        }
        urlMatches.sort(UrlMatch::compareTo);
    }

    public Optional<UrlMatch> urlMatch(String uri) {
        return urlMatches.stream().filter(e -> e.match(uri)).findFirst();
    }


    public static ProxyServer create(ProxyConf.Server serverConf, ProxyContext proxyContext) throws MalformedURLException {
        ProxyServer proxyServer = new ProxyServer();
        proxyServer.serverConf = serverConf;
        proxyServer.proxyContext = proxyContext;
        proxyServer.initProxyCline();
        ProxyClient proxyClient = ProxyClient.create(proxyServer.clinetBootstrap, proxyServer.workerGroup);
        proxyServer.serverBootstrap
                .childHandler(new ChannelInitializer<Channel>() {
                    @Override
                    protected void initChannel(Channel ch) throws Exception {
                        ChannelPipeline cp = ch.pipeline();
                        //请求解码器
                        cp.addLast("http-decoder", new HttpRequestDecoder());
                        // 响应转码器
                        cp.addLast("http-encoder", new HttpResponseEncoder());
                        // 将HTTP消息的多个部分合成一条完整的HTTP消息，对于报文较长的请求需要设置，短请求可以不设置
                        cp.addLast("http-aggregator", new HttpObjectAggregator(1024 * 1024));
                        // 压缩内容
                        cp.addLast("http-content-compressor", new HttpContentCompressor());

                        cp.addLast("http-proxy", new SimpleChannelInboundHandler<FullHttpRequest>() {
                            @Override
                            public void channelRead0(ChannelHandlerContext ctx, FullHttpRequest msg) throws Exception {
                                Channel sc = ctx.channel();
                                String uri = msg.uri();
                                String reqPath = "";
                                proxyServer.urlMatch(uri)
                                        .flatMap(e -> proxyContext.findClientUps(e.getHostname()))
                                        .map(ups -> proxyClient.createHttpClient(sc, ups))
                                        .ifPresentOrElse(httpClient -> {
                                            DefaultFullHttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, uri, msg.content().copy());
                                            for (Map.Entry<String, String> header : msg.headers()) {
                                                if (HttpHeaderNames.HOST.contentEqualsIgnoreCase(header.getKey())) {
                                                    continue;
                                                }
                                                request.headers().add(header.getKey(), header.getValue());
                                            }
                                            request.headers().add(HttpHeaderNames.HOST, httpClient.getHost());

                                            httpClient.writeAndFlush(request);
                                        }, () -> {
                                            DefaultFullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.NOT_FOUND);
                                            response.headers().add(HttpHeaderNames.CONTENT_LENGTH, 0);
                                            ctx.writeAndFlush(response);
                                        });
                            }
                        });
                    }
                });
        proxyServer.serverBootstrap.bind(serverConf.getListen())
                .addListener((ChannelFutureListener) future -> {
                    if (future.isSuccess()) {
                        System.out.println(serverConf.getServerName() + ":" + serverConf.getListen() + " 启动成功");
                        proxyServer.sc = future.channel();
                    } else {
                        throw new RuntimeException(future.cause());
                    }
                });
        return proxyServer;
    }

    @Override
    public void close() throws IOException {
        if (sc != null)
            sc.close();
        bossGroup.shutdownGracefully();
        workerGroup.shutdownGracefully();
    }
}
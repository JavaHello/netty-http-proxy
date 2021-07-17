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
import java.util.*;

public class ProxyServer implements Closeable {
    final EventLoopGroup bossGroup;
    final EventLoopGroup workerGroup;
    final ServerBootstrap serverBootstrap = new ServerBootstrap();
    final Bootstrap clinetBootstrap = new Bootstrap();

    private Channel sc;
    private ProxyConf.Server serverConf;

    public ProxyServer() {
        bossGroup = new NioEventLoopGroup();
        workerGroup = new NioEventLoopGroup();
        serverBootstrap.group(bossGroup, workerGroup).channel(NioServerSocketChannel.class);
    }

    public void initProxyCline() {

    }

    public static ProxyServer create(ProxyConf.Server serverConf, ProxyContext proxyContext) throws MalformedURLException {
        ProxyServer proxyServer = new ProxyServer();
        proxyServer.serverConf = serverConf;

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

                            private final Map<String, List<ProxyClient>> clientMap = new HashMap<>();

                            @Override
                            public void channelActive(ChannelHandlerContext ctx) throws Exception {
                                Channel sc = ctx.channel();
                                for (Map.Entry<String, ProxyConf.Location> locationEntry : serverConf.getLocation().entrySet()) {
                                    String key = locationEntry.getKey();
                                    ProxyConf.Location value = locationEntry.getValue();
                                    String proxyPass = value.getProxyPass();
                                    URL url = new URL(proxyPass);
                                    Optional<List<String>> upstream = proxyContext.findUpstream(url.getHost());
                                    upstream.ifPresentOrElse(e -> {
                                        clientMap.put(key, ProxyClientHelper.createClients(e, sc, url.getPath()));
                                    }, () -> {
                                        int port = 80;
                                        if (url.getPort() > 1) {
                                            port = url.getPort();
                                        }
                                        ProxyClient proxyClient = ProxyClient.create(sc, url.getHost(), port, url.getPath());
                                        clientMap.put(key, Arrays.asList(proxyClient));
                                    });
                                }
                            }

                            @Override
                            public void channelRead0(ChannelHandlerContext ctx, FullHttpRequest msg) throws Exception {

                                String uri = msg.uri();
                                String reqPath = "";
                                Optional.ofNullable(clientMap.get(uri))
                                        .ifPresentOrElse(e -> {
                                            ProxyClient proxyClient = e.get(0);
                                            DefaultFullHttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, proxyClient.path(reqPath), msg.content().copy());
                                            for (Map.Entry<String, String> header : msg.headers()) {
                                                if (HttpHeaderNames.HOST.contentEqualsIgnoreCase(header.getKey())) {
                                                    continue;
                                                }
                                                request.headers().add(header.getKey(), header.getValue());
                                            }
                                            request.headers().add(HttpHeaderNames.HOST, proxyClient.address);
                                            proxyClient.writeAndFlush(request);
                                        }, () -> {
                                            DefaultFullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.NOT_FOUND);
                                            response.headers().add(HttpHeaderNames.CONTENT_LENGTH, 0);
                                            ctx.writeAndFlush(response);
                                        });
                            }

                            @Override
                            public void channelInactive(ChannelHandlerContext ctx) throws Exception {
                                for (Map.Entry<String, List<ProxyClient>> clientEntry : clientMap.entrySet()) {
                                    for (ProxyClient client : clientEntry.getValue()) {
                                        client.close();
                                    }
                                }
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
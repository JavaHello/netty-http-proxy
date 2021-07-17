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

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpContentDecompressor;
import io.netty.handler.codec.http.HttpObjectAggregator;

import java.net.InetSocketAddress;

public class ProxyClient {
    static int maxContentLength = 1024 * 1024;
    String address;
    int port;
    final Bootstrap clientBootstrap;
    Channel ch;

    private String prefix;
    private ProxyClient() {
        clientBootstrap = new Bootstrap();
    }

    public String path(String reqPath) {
        return prefix + reqPath;
    }
    public static ProxyClient create(Channel sc, String address, int port, String prefix) {
        ProxyClient proxyClient = new ProxyClient();
        proxyClient.address = address;
        proxyClient.port = port;
        proxyClient.prefix = prefix;
        proxyClient.clientBootstrap
                .group(sc.eventLoop())
                .channel(NioSocketChannel.class)
                .handler(new ChannelInitializer<Channel>() {
                    @Override
                    protected void initChannel(Channel ch) throws Exception {
                        ChannelPipeline cp = ch.pipeline();
                        //包含编码器和解码器
                        cp.addLast(new HttpClientCodec());
                        //聚合
                        cp.addLast(new HttpObjectAggregator(maxContentLength));
                        //解压
                        cp.addLast(new HttpContentDecompressor());
                        cp.addLast(new ChannelInboundHandlerAdapter() {
                            @Override
                            public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                                sc.write(msg);
                            }
                            @Override
                            public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
                                sc.flush();
                            }
                        });
                    }
                });
        return proxyClient;
    }

    public ChannelFuture connect() {
        return clientBootstrap.connect(new InetSocketAddress(address, port)).addListener((ChannelFutureListener) future -> {
            if (future.isSuccess()) {
                ch = future.channel();
            } else {
                future.cause().printStackTrace();
            }
        });
    }

    public void writeAndFlush(Object msg) {
        if (ch != null) {
            ch.writeAndFlush(msg);
        } else {
            this.connect().addListener((ChannelFutureListener) future -> {
                if (future.isSuccess()) {
                    future.channel().writeAndFlush(msg);
                } else {
                    future.cause().printStackTrace();
                }
            });
        }
    }

    public void close() {
        if (ch != null) {
            ch.close();
        }
    }
}

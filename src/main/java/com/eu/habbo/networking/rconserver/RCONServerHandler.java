package com.eu.habbo.networking.rconserver;


import com.eu.habbo.Emulator;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.InetSocketAddress;

public class RCONServerHandler extends ChannelInboundHandlerAdapter {

    private static final Logger LOGGER = LoggerFactory.getLogger(RCONServerHandler.class);

    @Override
    public void channelRegistered(ChannelHandlerContext ctx) throws Exception {
        String address = getRemoteAddress(ctx);

        if (isAllowedAddress(address)) {
            LOGGER.info("RCON Remote connection accepted: {}", address);
            return;
        }

        ctx.channel().close();

        LOGGER.warn("RCON Remote connection closed: {}. IP not allowed! Allowed list: {}", address, Emulator.getRconServer().allowedAdresses);
    }

    private static boolean isAllowedAddress(String address) {
        if (address == null || address.isEmpty()) {
            return false;
        }

        for (String rawRule : Emulator.getRconServer().allowedAdresses) {
            String rule = rawRule == null ? "" : rawRule.trim();
            if (rule.isEmpty()) {
                continue;
            }

            if ("*".equals(rule)) {
                return true;
            }

            if (rule.equalsIgnoreCase(address)) {
                return true;
            }

            if (rule.contains("/") && matchesIpv4Cidr(address, rule)) {
                return true;
            }
        }

        return false;
    }

    private static boolean matchesIpv4Cidr(String ip, String cidr) {
        String[] parts = cidr.split("/", 2);
        if (parts.length != 2) {
            return false;
        }

        Integer ipLong = ipv4ToLong(ip);
        Integer networkLong = ipv4ToLong(parts[0]);
        if (ipLong == null || networkLong == null) {
            return false;
        }

        int prefix;
        try {
            prefix = Integer.parseInt(parts[1]);
        } catch (NumberFormatException e) {
            return false;
        }

        if (prefix < 0 || prefix > 32) {
            return false;
        }

        int mask = prefix == 0 ? 0 : (-1 << (32 - prefix));
        return (ipLong & mask) == (networkLong & mask);
    }

    private static Integer ipv4ToLong(String ipv4) {
        String[] octets = ipv4.split("\\.");
        if (octets.length != 4) {
            return null;
        }

        int result = 0;
        for (String octet : octets) {
            int value;
            try {
                value = Integer.parseInt(octet);
            } catch (NumberFormatException e) {
                return null;
            }
            if (value < 0 || value > 255) {
                return null;
            }
            result = (result << 8) | value;
        }
        return result;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        ByteBuf data = (ByteBuf) msg;

        byte[] d = new byte[data.readableBytes()];
        data.getBytes(0, d);
        String message = new String(d);
        String address = getRemoteAddress(ctx);
        Gson gson = new Gson();
        String response = "ERROR";
        String key = "";
        try {
            JsonObject object = gson.fromJson(message, JsonObject.class);
            key = object.get("key").getAsString();
            LOGGER.info("RCON Received key={} from {}", key, address);
            response = Emulator.getRconServer().handle(ctx, key, object.get("data").toString());
        } catch (ArrayIndexOutOfBoundsException e) {
            LOGGER.error("Unknown RCON Message: {}", key);
        } catch (Exception e) {
            LOGGER.error("Invalid RCON Message from {}: {}", address, message);
            e.printStackTrace();
        }

        LOGGER.info("RCON Responding key={} to {} with {} bytes", key, address, response.getBytes().length);

        ChannelFuture f = ctx.channel().write(Unpooled.copiedBuffer(response.getBytes()), ctx.channel().voidPromise());
        ctx.channel().flush();
        ctx.flush();
        f.channel().close();
        data.release();
    }

    private static String getRemoteAddress(ChannelHandlerContext ctx) {
        String address = "";
        try {
            InetSocketAddress remote = (InetSocketAddress) ctx.channel().remoteAddress();
            InetAddress inetAddress = remote.getAddress();
            if (inetAddress != null) {
                address = inetAddress.getHostAddress();
            }
        } catch (Exception ignored) {
            // Keep empty string and reject/log above.
        }
        return address;
    }
}

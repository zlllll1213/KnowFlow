package com.knowflow.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.util.Arrays;
import java.util.List;

@Component
public class ClientIpResolver {

    private final List<String> trustedProxyCidrs;

    public ClientIpResolver(@Value("${knowflow.trusted-proxy-cidrs:}") String trustedProxyCidrs) {
        this.trustedProxyCidrs = Arrays.stream((trustedProxyCidrs == null ? "" : trustedProxyCidrs).split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .toList();
    }

    public String resolve(HttpServletRequest request) {
        String remoteAddr = request.getRemoteAddr();
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor == null || forwardedFor.isBlank() || !isTrustedProxy(remoteAddr)) {
            return remoteAddr;
        }
        return forwardedFor.split(",")[0].trim();
    }

    private boolean isTrustedProxy(String remoteAddr) {
        return trustedProxyCidrs.stream().anyMatch(cidr -> matchesCidr(remoteAddr, cidr));
    }

    private boolean matchesCidr(String address, String cidr) {
        try {
            if (!cidr.contains("/")) {
                return InetAddress.getByName(address).equals(InetAddress.getByName(cidr));
            }
            String[] parts = cidr.split("/", 2);
            byte[] ip = InetAddress.getByName(address).getAddress();
            byte[] network = InetAddress.getByName(parts[0]).getAddress();
            if (ip.length != network.length) {
                return false;
            }
            int prefix = Integer.parseInt(parts[1]);
            int fullBytes = prefix / 8;
            int remainingBits = prefix % 8;
            for (int i = 0; i < fullBytes; i++) {
                if (ip[i] != network[i]) {
                    return false;
                }
            }
            if (remainingBits == 0) {
                return true;
            }
            int mask = 0xFF << (8 - remainingBits);
            return (ip[fullBytes] & mask) == (network[fullBytes] & mask);
        } catch (Exception e) {
            return false;
        }
    }
}

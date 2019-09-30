package com.doodream.robustdns;

import java.net.InetAddress;

public class DnsRecord {
    private static final DnsRecord FAIL = new DnsRecord();
    boolean isSuccessful;
    InetAddress address;
    long expireAt;

    public static DnsRecord fail() {
        return FAIL;
    }

    public static DnsRecord success(InetAddress resolved, long expireAt) {
        return new DnsRecord(resolved, expireAt);
    }

    private DnsRecord() {
        isSuccessful = false;
    }

    private DnsRecord(InetAddress resolved, long expireAt) {
        isSuccessful = true;
        address = resolved;
        this.expireAt = expireAt;
    }

    public boolean isSuccessful() {
        return isSuccessful;
    }

    public InetAddress getAddress() {
        return address;
    }
}

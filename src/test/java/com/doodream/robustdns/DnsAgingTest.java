package com.doodream.robustdns;

import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.TimeUnit;

@RunWith(Parameterized.class)
public class DnsAgingTest {
    private static final String[] CREDIBLE_DNS_ADDRESS = {
            "8.8.8.8",
            "8.8.4.4",
            "1.1.1.1",
            "205.251.198.30",
            "168.126.63.1"   // Ill performing DNS (KT Main DNS address)
    };

    private static RobustDnsResolver resolver;

    static {
        try {
            resolver = RobustDnsResolver.builder()
                        .failoverDns(CREDIBLE_DNS_ADDRESS)
                        .cache(true)
                    .updateOnExpire(true)
                        .cacheTimeout(1L, TimeUnit.SECONDS)
                        .timeout(10L, TimeUnit.SECONDS)
                        .build();
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }
    }

    @BeforeClass
    public static void init() {
    }

    @Parameterized.Parameters
    public static Collection<Object> data() {
        return Arrays.asList(new Object[100][]);
    }


    @Test(timeout = 5000L)
    public void test_lookup() throws Exception {
        System.out.println(resolver.resolve("www.google.com").blockingGet().getAddress());
    }
}

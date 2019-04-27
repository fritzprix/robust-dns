package com.doodream.robustdns;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

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
            "168.126.63.1"
    };

    private final RobustDnsResolver resolver;

    @Parameterized.Parameters
    public static Collection<Object> data() {
        return Arrays.asList(new Object[100][]);
    }

    public DnsAgingTest() throws Exception {
        resolver = RobustDnsResolver.builder()
                .failoverDns(
                        "168.126.63.1",
                        "8.8.8.8",
                        "8.8.4.4",
                        "1.1.1.1"
                )
                .cache(true)
                .timeout(10L, TimeUnit.SECONDS)
                .build();
    }

    @Test(timeout = 5000L)
    public void test_lookup() throws Exception{
        System.out.println(resolver.resolve("www.google.com").blockingGet());
    }
}

package com.doodream.robustdns;

import io.reactivex.Observable;
import io.reactivex.Single;
import org.xbill.DNS.*;

import java.io.IOException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class RobustDnsResolver {

    private static final String[] CREDIBLE_DNS_ADDRESS = {
            "8.8.8.8",
            "8.8.4.4",
            "1.1.1.1",
            "205.251.198.30"
    };

    private final ExtendedResolver resolver;


    public RobustDnsResolver(String ...dnsUrls) throws UnknownHostException {
        List<String> urls = new ArrayList<>();
        if(dnsUrls != null && dnsUrls.length > 0) {
            urls.addAll(Arrays.asList(dnsUrls));
        }
        urls.addAll(Arrays.asList(CREDIBLE_DNS_ADDRESS));
        String[] allDnsUrls = urls.toArray(new String[0]);
        resolver = new ExtendedResolver(Observable.fromArray(allDnsUrls)
                .map(dnsAddress -> new SimpleResolver(dnsAddress))
                .toList()
                .blockingGet()
                .toArray(new Resolver[0]));
    }

    public RobustDnsResolver() throws UnknownHostException {
        this(new String[0]);
    }

    public Single<String> resolve(String name) throws IOException {
        return Single.create(emitter -> {
            final Record question = Record.newRecord(Name.concatenate(Name.fromString(name), Name.root), Type.A, DClass.IN, TTL.MAX_VALUE);
            final Message query = Message.newQuery(question);
            resolver.sendAsync(query, new ResolverListener() {
                @Override
                public void receiveMessage(Object o, Message message) {
                    Record[] records = message.getSectionArray(Section.ANSWER);
                    if(records != null || records.length > 0) {
                        emitter.onSuccess(records[0].rdataToString());
                    } else {
                        emitter.onError(new UnknownHostException(String.format(Locale.ENGLISH, "%s not found", name)));
                    }
                }

                @Override
                public void handleException(Object o, Exception e) {
                    emitter.onError(e);
                }
            });
        });

    }
}

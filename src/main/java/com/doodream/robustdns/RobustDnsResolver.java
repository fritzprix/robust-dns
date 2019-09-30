package com.doodream.robustdns;

import io.reactivex.Observable;
import io.reactivex.Single;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.functions.Function;
import io.reactivex.schedulers.Schedulers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xbill.DNS.*;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.PortUnreachableException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class RobustDnsResolver {

    private static final Logger Log = LoggerFactory.getLogger(RobustDnsResolver.class);
    private boolean updateOnExpire;
    private long cacheTimeoutInMills;


    private final ConcurrentHashMap<String, DnsRecord> cache = new ConcurrentHashMap<>();
    private boolean cacheEnabled;
    private final CompositeDisposable disposable = new CompositeDisposable();
    private Resolver[] rsv;

    public static class Builder {
        private final RobustDnsResolver resolver = new RobustDnsResolver();
        private List<String> urls;
        private long timeout;
        private TimeUnit timeUnit;
        private boolean cacheEnabled;
        private long cacheTimeoutInMills;
        private boolean autoUpdate;

        public Builder() { }

        public Builder failoverDns(String ...dnsUrls) throws UnknownHostException {
            List<String> urls = new ArrayList<>();
            if(dnsUrls != null && dnsUrls.length > 0) {
                urls.addAll(Arrays.asList(dnsUrls));
            }
            this.urls = urls;
            return this;
        }

        public Builder timeout(long timeout, TimeUnit timeUnit) {
            this.timeout = timeUnit.toMillis(timeout);
            this.timeUnit = timeUnit;
            return this;
        }

        public Builder updateOnExpire(boolean enable) {
            this.autoUpdate = enable;
            return this;
        }

        public Builder cacheTimeout(long timeout, TimeUnit timeUnit) {
            this.cacheTimeoutInMills = timeUnit.toMillis(timeout);
            return this;
        }

        public Builder cache(boolean enable) {
            this.cacheEnabled = enable;
            return this;
        }

        public RobustDnsResolver build() throws UnknownHostException {

            resolver.cacheEnabled = cacheEnabled;
            List<Resolver> resolvers = new ArrayList<>(Arrays.asList(Lookup.getDefaultResolver()));

            if(!urls.isEmpty()) {

                resolver.rsv = Observable.fromIterable(urls)
                        .map(url -> new SimpleResolver(url))
                        .cast(Resolver.class)
                        .toList()
                        .doOnSuccess(rsvs -> rsvs.addAll(resolvers))
                        .blockingGet()
                        .toArray(new Resolver[0]);
            } else {
                resolver.rsv = resolvers.toArray(new Resolver[0]);
            }
            if(timeUnit != null) {
                for (Resolver resv : resolver.rsv) {
                    resv.setTimeout((int) timeUnit.toSeconds(timeout), (int) (timeUnit.toMillis(timeout) - (timeUnit.toSeconds(timeout) * 1000)));
                }
            }
            resolver.updateOnExpire = autoUpdate;
            resolver.cacheTimeoutInMills = cacheTimeoutInMills;
            return resolver;
        }

    }

    public static Builder builder() {
        return new Builder();
    }

    private RobustDnsResolver() {
    }

    public Single<DnsRecord> resolve(String name) {
        if(cache.containsKey(name)) {
            final DnsRecord record = cache.get(name);
            if(record.expireAt < System.currentTimeMillis()) {
                DnsRecord old = cache.remove(name);
                if(updateOnExpire) {
                    resolve(name).subscribe();
                    return Single.just(old);
                } else {
                    return resolve(name);
                }
            }
            return Single.just(record);
        }
        if(name == null || name.isEmpty()) {
            return Single.error(new UnknownHostException("invalid hostname : empty"));
        }
        try {
            final Record question = Record.newRecord(Name.concatenate(Name.fromString(name), Name.root), Type.A, DClass.IN, TTL.MAX_VALUE);
            final Message query = Message.newQuery(question);
            return Observable.fromArray(rsv)
                    .map(resolver -> resolver.send(query))
                    .map(message -> {
                        final Record[] records = message.getSectionArray(Section.ANSWER);
                        if (records == null || records.length == 0) {
                            return DnsRecord.fail();
                        }
                        try {
                            final InetAddress resolved = Inet4Address.getByName(records[0].rdataToString());
                            return DnsRecord.success(resolved, System.currentTimeMillis() + cacheTimeoutInMills);
                        } catch (UnknownHostException e) {
                            return DnsRecord.fail();
                        }
                    }).onErrorReturn(new Function<Throwable, DnsRecord>() {
                        @Override
                        public DnsRecord apply(Throwable throwable) throws Exception {
                            if (throwable instanceof PortUnreachableException) {
                                try {
                                    final InetAddress resolved = Address.getByName(name);
                                    return DnsRecord.success(resolved, System.currentTimeMillis() + cacheTimeoutInMills);
                                } catch (Exception ignore) {
                                    // failure case will be handled by failover (java default resolver)
                                }
                            }


                            try {
                                return DnsRecord.success(InetAddress.getByName(name), System.currentTimeMillis() + cacheTimeoutInMills);
                            } catch (UnknownHostException ue) {
                                return DnsRecord.fail();
                            }
                        }
                    })
                    .first(DnsRecord.fail())
                    .doOnSuccess(record -> {
                        if (record.isSuccessful() && cacheEnabled) {
                            cache.put(name, record);
                        }
                    })
                    .subscribeOn(Schedulers.io());

        } catch (Exception e) {
            return Single.just(DnsRecord.fail());
        }
    }
}

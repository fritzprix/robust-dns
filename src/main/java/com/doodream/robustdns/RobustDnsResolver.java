package com.doodream.robustdns;

import io.reactivex.Observable;
import io.reactivex.Single;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.schedulers.Schedulers;
import org.xbill.DNS.*;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.PortUnreachableException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class RobustDnsResolver {

    private boolean updateOnExpire;
    private long cacheTimeoutInMills;

    public static class DnsRecord {
        InetAddress address;
        long expireAt;

        public DnsRecord(InetAddress resolved, long expireAt) {
            address = resolved;
            this.expireAt = expireAt;
        }
    }

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

    public Single<InetAddress> resolve(String name) {
        if(cache.containsKey(name)) {
            final DnsRecord record = cache.get(name);
            if(record.expireAt < System.currentTimeMillis()) {
                cache.remove(name);
                if(updateOnExpire) {
                    resolve(name).subscribe();
                    return Single.just(record.address);
                } else {
                    return resolve(name);
                }
            }
        }
        if(name == null || name.isEmpty()) {
            return Single.error(new UnknownHostException("invalid hostname : empty"));
        }
        return Single.<InetAddress>create(emitter -> {
            final Record question = Record.newRecord(Name.concatenate(Name.fromString(name), Name.root), Type.A, DClass.IN, TTL.MAX_VALUE);
            final Message query = Message.newQuery(question);

            for (Resolver resolver : rsv) {
                resolver.sendAsync(query, new ResolverListener() {
                    @Override
                    public void receiveMessage(Object o, Message message) {
                        Record[] records = message.getSectionArray(Section.ANSWER);

                        if(records != null) {
                            if(records.length == 0) {
                                return;
                            }
                            try {
                                final InetAddress resolved = Inet4Address.getByName(records[0].rdataToString());
                                DnsRecord record = new DnsRecord(
                                        resolved,
                                        System.currentTimeMillis() + cacheTimeoutInMills
                                );

                                if(cacheEnabled) {
                                    cache.put(name, record);
                                }
                                emitter.onSuccess(resolved);
                            } catch (UnknownHostException e) {
                                emitter.onError(e);
                            }
                        } else {
                            emitter.onError(new UnknownHostException(String.format(Locale.ENGLISH, "%s not found", name)));
                        }
                    }

                    @Override
                    public void handleException(Object o, Exception e) {
                        if(e instanceof PortUnreachableException) {
                            disposable.add(Observable.just(name)
                                    .subscribeOn(Schedulers.io())
                                    .subscribe(unresolved -> {
                                        try {
                                            final InetAddress resolved = Address.getByName(name);
                                            DnsRecord record = new DnsRecord(
                                                    resolved,
                                                    System.currentTimeMillis() + cacheTimeoutInMills
                                            );
                                            if (cacheEnabled) {
                                                cache.put(name, record);
                                            }
                                            emitter.onSuccess(resolved);
                                        } catch (Exception uke) {
                                            // emitter.onError(uke);
                                        }
                                    }));
                            return;
                        }
                        e.printStackTrace();
                    }
                });
            }
        }).timeout(10L, TimeUnit.SECONDS).subscribeOn(Schedulers.io());
    }
}

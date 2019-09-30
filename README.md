# ROBUST DNS
> small proof of concept for client side DNS failover 
 
## Problem Statement 
> DNS setting on user's environment can cause huge impact on overall user experience. some ill performing DNS are still 
widely configured general user environment. (there are a lot of articles like [here](https://blog.naver.com/star-com/221481249044), recommending DNS server configuration update to ill performing one). 
Most widely adopted solution for problem is client side DNS caching with optimal cache time considering typical use case, which can relieve the situation though, there is a trade-off with security issues relating to DNS exploit (e.g. DNS Spoof)
so just extending caching duration is not optimal solution for the problem. 

 

## Solution
- Sending DNS Queries to multiple DNS Servers concurrently
> By sending concurrent queries to multiple DNS servers (for example, default DNS server and a few well-known public DNS servers) and accepting response which arrives first, DNS response is guaranteed within certain boundary of time while minimizing 
risk of DNS request failure  
- Asynchronous refresh on cache expiration
> Not just extending cache duration, but by updating the DNS record when it's expired (responding with stale result first), most DNS queries are guaranteed to be handled by local DNS caches while reducing risk of DNS cache poisoning attack. 

## Test Method 
> One of ill performing DNS server in Korea is KT(Korea Telecom) DNS server whose IP address is 168.126.63.1, which is also one of the widely used DNS server. 
so KT DNS is chosen to configure test environment. it shows decent response time typically, however, it doesn't respond at all occasionally. 
- Test Devices : SM-G960 (Samsung Galaxy S9 mobile phone)
- Network connection : Wi-Fi (No Packet Drop condition)
- Access Point : IPTIME A5004NS-M
- DNS#1 : 168.126.63.1 
- DNS#2 : 4.4.4.4 (Intended Invalid Address)
- Time stamping method : Java System.currentTimeMills() 

> To verify multiple concurrent queries concept, all the DNS queries should be missed in local DNS cache. Android has 10 min. of DNS caching time as a default. so test is performed with interval of 15 min.
A test program performs DNS resolution by calling InetAddress::getByName() which is considered a system default implementation of DNS resolution, while other test program uses the POC implementation. Both of them perform DNS resolution trial given interval (which is 15 min.)  
 
## Test Result 
> Test are performed with 80 reps for each case which takes almost 20 hours parallel. there was 1 DNS failure observed with system default resolver while no failure is observed at all for POC resolver throughout the test.
Regarding measured response time, the measured time varies quite dynamically for default resolver (min : 10 msec ~ max : 15 second), while it doesn't for POC resolver (min 28 msec ~ max : 518 msec).
moreover, the response time becomes very close to zero when the 'update cache on expire' is activated showing response time (min : 0 msec ~ max : 271 msec). note that the maximum response time in last case is for first DNS query which is essentially missed in local DNS cache.
the measured averages are 803.8, 118.3, 4.68 msec correspondingly.
- case 1 : InetAddress.getByName()
- case 2 : POC resolver 
- case 3 : POC resolver + proposed cache policy

| item  | case 1  |  case 2  |  case 3  |
|---|---|---|---|
| Avg. response time (msec)  | 803.8 | 118.3  |  4.68  |
| Max. response time (msec)  | 15025  | 518  | 271  |
| Min. response time (msec)  | 10  |  28 |  0 |
 
 


## Using Gradle
1. Add Repository
```groovy
allprojects {
    repositories {
        ...
        maven {
            url 'https://raw.githubusercontent.com/fritzprix/robust-dns/releases'
        }
        maven {
            url 'https://raw.githubusercontent.com/fritzprix/robust-dns/snapshots'
        }
        ...
    }
}
```
2. Add Dependency
```groovy
dependencies {
...
    implementation 'com.doodream:robust-dns:1.0.1'
    implementation 'dnsjava:dnsjava:2.1.8'
...
}
```

## dependencies 
 - dnsjava
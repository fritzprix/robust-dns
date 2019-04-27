# ROBUST DNS
 
## Problem Statement 
> DNS setting on user's environment can cause huge impact on overall user experience. some ill performing DNS are still 
widely configured general user environment. (there are a lot of article [here](https://blog.naver.com/star-com/221481249044) recommending change DNS server address to ill performing one) one of widely accepted the solution for the problem is dns caching, however, it makes user device 
more vulnerable to DNS poisoning. so here I propose another alternative for the problem. 

## Approach : Use response arrived first
> By sending concurrent DNS query to multiple DNS provider other than system default and accepting response which arrives first can provide guarantee on max response time


## dependencies 
 - dnsjava
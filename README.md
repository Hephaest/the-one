Table of Contents
=================
* [The ONE](#the-one)
* [Development of DTN Routing Protocol](#development-of-dtn-routing-protocol)
   * [Utility Routing](#utility-routing)
   * [Queueing management](#queueing-management)
         
# The ONE

The Opportunistic Network Environment simulator.

For introduction and releases, see [the ONE homepage at GitHub](http://akeranen.github.io/the-one/).

For instructions on how to get started, see [the README](https://github.com/akeranen/the-one/wiki/README).

The [wiki page](https://github.com/akeranen/the-one/wiki) has the latest information.

# Development of DTN Routing Protocol
For further test, `default_settings.txt` has been changed as follows. I have already added `EnergyModel settings` to simulate the real scenarios:

```java
## EnergyModel settings

# Visitor pedestrians
Group1.initialEnergy = 80000
Group1.transmitEnergy = 1
Group1.scanEnergy = 1
Group1.scanResponseEnergy = 1

# Bikers
Group2.initialEnergy = 110000
Group2.transmitEnergy = 1
Group2.scanEnergy = 1
Group2.scanResponseEnergy = 1

# Student pedestrians
Group3.initialEnergy = 100000
Group3.transmitEnergy = 1
Group3.scanEnergy = 1
Group3.scanResponseEnergy = 1

# The Tram groups
Group4.initialEnergy = 200000
Group4.transmitEnergy = 1.5
Group4.scanEnergy = 1.5
Group4.scanResponseEnergy = 1.5
Group5.initialEnergy = 200000
Group5.transmitEnergy = 1.5
Group5.scanEnergy = 1.5
Group5.scanResponseEnergy = 1.5
Group6.initialEnergy = 200000
Group6.transmitEnergy = 1.5
Group6.scanEnergy = 1.5
Group6.scanResponseEnergy = 1.5
```
## Utility Routing

This protocol combines both ***Probabilistic*** routing and ***Spray and Wait*** routing protocol to define the utility of the mobile node as a metric for next-hop selection.<br>

This protocol improves performance by considering the following factors:<br>
<UL>
  <LI/> 1. Energy.
  <LI/> 2. Mobility.
  <LI/> 3. Subnet (Location).
  <LI/> 4. Queue.
</UL>

In utility routing, the **overlap** is the **worst-case** that we need to avoid it. Besides, the mobility is used to predict the degree of delivery possibility while energy indicates how much we can trust this router. Further, if another router has more energy than the current router which means it has a higher possibility of delivery. However, this requirement is not compulsory. As for the queue, we can drop the message that the other one is unable to buffer from the selected connection(**reduce computation overhead**).

Given to simulation results, it shows a higher delivery rate with lower overhead ratio compared with the above protocols.

<br>
<p align="center"><img src="https://github.com/Hephaest/the-one/blob/master/images/R1_delivery.jpg" height="280dp" /><img src="https://github.com/Hephaest/the-one/blob/master/images/R1_overhead.jpg" height="280dp"  /></p>
<br>
<br>

## Queueing management
In the perspective of queueing management, the message with the lowest possibility for delivery will be discarded. You can easily find the corresponding algorithm to achieve intelligent drop. The core of this algorithm is to check the following factors:
<UL>
  <LI/> The <b>relationship</b> between TTL and the destination of the message. E.g. if the message leaves with a small TTL but it still has a long way to transfer, then it should be discarded.
  <LI/> The message <b>size</b>. E.g. if the message is too large then it has a lower possibility to be transferred successfully due to DTN.
</UL>

Given to simulation results, with decreasing buffer size, it shows **higher robustness** in delivery possibility and overhead compared with the above protocols.

<p align="center"><img src="https://github.com/Hephaest/the-one/blob/master/images/R2_delivery.jpg" height="280dp" /><img src="https://github.com/Hephaest/the-one/blob/master/images/R2_overhead.png" height="280dp" /></p>

pretixSCAN Proxy
================

On-site server component for larger-scale pretixSCAN deployments.

Motivation
----------

By default, our scanning apps [pretixSCAN](https://pretix.eu/about/en/scan) communicate directly with the pretix server on the internet. This way, they can make sure that no ticket is scanned twice and that they always have live data:

![Online scanning](res/online.png?raw=true)

Obviously, this isn't practicable for lots of events. Many events, especially large ones, take place in environments with slow and unreliable internet connection and it's just not an option to rely on the internet to work. Therefore, pretixSCAN downloads all ticket data to the local device and supports an *Offline mode* in which it locally decides whether a ticket is valid and then syncs with the server periodically:

![Offline scanning](res/offline.png?raw=true)

This is great for smaller events, because it just works without any infrastructure requirements, but if you try to scale it up to large events, there are a number of downsides to this approach:

* Downloading all ticket data to the devices takes a long time if you have lots of ticket data.
* Searching through the ticket data locally can also take quite some time if you have ten thousands of tickets since mobile devices often don't have fast storage and CPUs.
* Having all this personal data on the devices is a problem when you loose a device.
* *Offline mode* allows people to trick you by redeeming the same ticket twice in short sequence with different scanners.

**pretixSCAN Proxy** is our solution to all of these problems. The idea is to set up a server in your local WiFi network that relays the communication between the devices and the pretix server and basically takes over the function of the local storage in *Offline mode*:

![Proxy mode](res/proxy.png?raw=true)

This allows you to get the best of both worlds – as long as there is a reliable local network (of course there's a catch): No data is stored on the devices, you can use a fast server for the local caching, internet connection is still optional and every ticket can only be scanned once *per proxy* (you could use multiple, if you want).

A word of caution
-----------------

This is a tool for really advanced usage. It's made to be operated by people with deep technical knowledge. If you have thousands of tickets and need a rock-solid check-in process without internet connection, we **strongly advice you to get professional support**.

If you think you need this, please reach out at support@pretix.eu and let's talk about your use case. 
We're also happy to provide you with rental hardware as well as remote or on-site support, so you can focus on your event.

Development status
------------------

This seems to be working but hasn't been used in production yet.
The API exposed by the proxy is currently supported by pretixSCAN on Android starting with version 1.0.2. It is not yet supported in pretixSCAN Desktop (but will be). Support for pretixSCAN on iOS is not planned.

System requirements
-------------------

* Java 8 or newer

* A PostgreSQL database

Build and run
-------------

To build the project, just use:

    ./gradlew jar
    
Then you can run the built JAR file:

    java \
        -Dpretixscan.database="jdbc:postgresql:scanproxy" \
        -Dpretixscan.baseurl="https://local.baseurl.com" \
        -jar server/build/libs/server-1.0-SNAPSHOT.jar

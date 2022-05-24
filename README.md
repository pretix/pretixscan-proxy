pretixSCAN Proxy
================

On-site server component for larger-scale pretixSCAN deployments.

A word of caution
-----------------

This is a tool for really advanced usage. The tool itself is made to be operated by people with comprehensive knowledge of the technical domain. Also, if you are in the situation of planning a rock-solid check-in process for thousands of participants, we **strongly advise you to get professional support** regardless of the tools in use. This is a **complex undertaking** and if you're not an expert with these things (or if you are an expert, but busy with other things during the event), **you are going to have a bad time**.

If you think you need this, please reach out at **support@pretix.eu** and let's talk about your use case. 
We're happy to provide you with rental hardware as well as remote or on-site support, so you can focus on your event.

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

Development status
------------------

pretixSCAN Proxy has been used in production. However, it currently does not follow a defined release cycle
and is currently mostly used in projects in close collaboration with our team.

System requirements
-------------------

* Java 8 or newer

* A PostgreSQL database

The API exposed by the proxy is currently supported by pretixSCAN on Android starting with version 1.0.2. and by pretixSCAN Desktop starting with version 1.1.0. Support for pretixSCAN on iOS is not planned.

Build and run
-------------

To build the project, just use:

    ./gradlew jar
    
Then you can run the built JAR file:

    java \
        -Dpretixscan.database="jdbc:postgresql:scanproxy" \
        -Dpretixscan.baseurl="https://local.baseurl.com" \
        -Dpretixscan.adminauth="admin:admin" \
        -Dpretixscan.autoOfflineMode="off" \
        -jar server/build/libs/server-1.0-SNAPSHOT.jar

For initial configuration, visit the web interface at http://localhost:7000 (or the domain of your reverse proxy)
and login with the admin credentials defined above.

Config options
--------------

===================================== ============================================
Property                              Description
===================================== ============================================
pretixscan.database                   PostgreSQL connection URL
pretixscan.baseurl                    Base URL the proxy will be reachable at
pretixscan.adminauth                  user:pass for management interface
pretixscan.autoOfflineMode            "off" for "always offline", "on" for "default
                                      to online, but switch to offline after repeated
                                      errors", or "1s", "2s", "5s", "10s", "15s", "20s"
                                      for "default to online, but switch to offline after
                                      repeated errors or after average scans longer than X"
===================================== ============================================

API
---

Beside the web interface and the endpoints powering it, the proxy server exposes two sets of API endpoints:

- A set of API endpoints that "mock" endpoints from the pretix server API. For example, you can query
  ``/api/v1/organizers/<org>/events/<event>/orders/`` and will get a valid, but always empty response. For other
  resources like items or check-in lists, you get a populated response. These API calls are not documented on
  purpose as they only exist to ensure compatibility with our pretixSCAN apps.
  
- A set of API endpoints special to the proxy at ``/proxyapi/v1/``. The ones that are intended to be used by third
  parties are documented below.
  
### Authentication

Authentication to the API closely follows the [Device authentication](https://docs.pretix.eu/en/latest/api/deviceauth.html#rest-deviceauth)
protocol pretix uses. The admin web interface allows to generate initialization tokens which can be converted into a device
token with the ``/api/v1/device/initialize`` endpoint. You can then supply these tokens to all subsequent calls in a
``Authorization: Device <token>`` header.

### Check

The check endpoint allows you to check a ticket for validity.

Sample request:

    POST /proxyapi/v1/rpc/<event>/<checkinlist_id>/check/ HTTP/1.1
    Content-Type: application/json

    {
      "ticketid": "barcode_content",
      "answers": [
        {
          "question": {
            "server_id": 1234
          },
          "value": "Foo"
        }
      ],
      "with_badge_data": false,
      "ignore_unpaid": false
    }

Sample response:

    HTTP/1.1 200 OK
    Content-Type: application/json

    {
      "type": "VALID",
      "ticket": "Entry pass",
      "variation": "Regular",
      "attendee_name": "John Doe",
      "seat": "Row 3, Seat 5",
      "message": "Additional message",
      "reasonExplanation": "Additional reason for RULES error code",
      "orderCode": "ABC123",
      "firstScanned": "2021-05-12T12:00:00.000Z",
      "addonText": "+ Workshop",
      "isRequireAttention": false,
      "isCheckinAllowed": false,
      "requiredAnswers": [
        {
          "question": {
             ...
          },
          "currentValue": "Foo"
        }
      ],
      "position": {
        ...  // pretix API orderposition object
      }
    }

``type`` can currently be any of ``VALID``, ``INVALID``, ``USED``, ``ERROR``, ``UNPAID``, ``CANCELED``, ``RULES``, ``REVOKED``, ``PRODUCT``, or ``ANSWERS_REQUIRED``.
All other fields are nullable.

### Search

The search endpoint allows you to search for tickets.

Sample request:

    POST /proxyapi/v1/rpc/<event>/<checkinlist_id>/search/ HTTP/1.1
    Content-Type: application/json

    {
      "query": "john",
      "page": 1
    }

Sample response:

    HTTP/1.1 200 OK
    Content-Type: application/json

    [
      {
        "secret": "barcode_content",
        "ticket": "Entry pass",
        "variation": "Regular",
        "attendee_name": "John Doe",
        "seat": "Row 3, Seat 5",
        "orderCode": "ABC123",
        "addonText": "+ Workshop",
        "status": "PAID",
        "isRedeemed": false,
        "isRequireAttention": false,
        "position": {
          ...  // pretix API orderposition object
        }
      }
    ]

``status`` can currently be any of ``PAID``, ``CANCELED``, or ``PENDING``.

### Status

The status endpoint allows you to access statistics.

Sample request:

    GET /proxyapi/v1/rpc/<event>/<checkinlist_id>/status/ HTTP/1.1

Sample response:

    HTTP/1.1 200 OK
    Content-Type: application/json

    {
      "event_name": "Museum",
      "totalTickets": 3,
      "alreadyScanned": 2,
      "currentlyInside": 2,
      "items": [
        {
          "id": 1234,
          "name": "Entry Pass",
          "total": 3,
          "checkins": 2,
          "admission": true,
          "variations": [
            {
              "id": 3214,
              "name": "Regular",
              "total": 3,
              "checkins": 2
            }
          ]
        }
      ]
    ]


License
-------

Copyright 2019-2021 rami.io GmbH

Released under the terms of the GNU General Public License v3

Maintained by the pretix team <support@pretix.eu>.
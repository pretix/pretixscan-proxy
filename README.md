pretixSCAN Proxy
================

System requirements
-------------------

* Java 8 or newer

* A PostgreSQL database

Build and run
-------------

To build the project, just use:

    ./gradlew jar
    
Then you can run the built JAR file:

    java -Dpretixscan.database="jdbc:postgresql:scanproxy" -jar server/build/libs/server-1.0-SNAPSHOT.jar

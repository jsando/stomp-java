# STOMP 1.0 Java Client / Server

Compatible with Java 1.5+ (including Android).

No external dependencies, builds to jarfile less than 20KB.

Serious refactoring of Java Stomp client code from codehaus.  Original code has a lot of multithreading bugs
and doesn't appear to have been maintained in years.

Borrowed slightly from RabbitMQ AMQ client (which essentially follows the same pattern as the codehaus Stomp client).

Optimized for small footprint, multithreading safety, and runtime memory.

Mainly targeted to high performance, low footprint messaging to ActiveMQ.  It may work with other stomp
servers.

## Features
 * tcp
 * ssl
 * udp multicast
 * No external dependencies!
 * Runs on java 1.5 or newer including Android

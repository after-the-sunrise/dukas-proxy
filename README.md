# dukas-proxy
[![Build Status][travis-icon]][travis-page]

**dukas-proxy** is a standalone server application for providing REST/WebSocket access to [Dukascopy][dukascopy-home]'s JForex trading platform.
The official [JForex SDK][dukascopy-wiki] allows the user to use JForex API by programming custom Java applications.
**dukas-proxy** server leverages this official SDK and provides REST/WebSocket access for the SDK.

* REST interface for simple request/response communication.
* WebSocket interface for real-time publish/subscribe stream messaging.
* Automatic connection management with JForex platform. (cf: connect, throttled-reconnect, instrument subscription, ...)
* Pure Java application, requiring no additional installations other than the Java Runtime Environment.


## Getting Started

### Account

First of all, a valid [Dukascopy][dukascopy-home] trading account is required for the API access.
If you do not have one yet, a demo account can be created on the fly.

The IP address of the proxy server machine needs to be registered to the account. 
This is mandatory, since API access from an unregistered IP address will need to provide a dynamic PIN code, 
which is not ideal for automated server applications.
IP whitelisting can be done via "Summary > My Account > Security Settings > IP registration" after logging into the official website. 

### Installation

Download the ZIP archive from [releases][github-releases] page, and unarchive into an arbitrary directory on the server.

Application parameters such as `username` and `password` can be provided from either the command-line arguments and/or a configuration file.
Configuration file templates can be found under `./etc/` directory. Logging configuration file is optional, which will use console output as its default logger.  
Copy and rename the template files into the `./lib/conf/` directory and adjust the parameters accordingly.

```shell script
#
# Create classpath directory for customized configuration files.
#
mkdir ./lib/conf

#
# Copy and rename the template files.
#
cp ./etc/dukas-proxy-template.properties ./lib/conf/dukas-proxy.properties
cp ./etc/logback-template.xml ./lib/conf/logback.xml

#
# Edit the configurations accordingly.
# 
vi ./lib/conf/dukas-proxy.properties
```

```properties
dukas-proxy.credential.user=DemoUser
dukas-proxy.credential.pass=DemoPass
```

Other configuration parameters and defaults values can be found in `com.after_sunrise.dukascopy.proxy.Config.java` file.
 
The resulting directory/file structure should be as follows:

```shell script
./LICENSE
./README.md
./bin/dukas-proxy
./bin/dukas-proxy.bat
./etc/*
./lib/*.jar
./lib/conf/dukas-proxy.properties
./lib/conf/logback.xml
``` 

### Launch

Java Runtime Environment 11 or later is required. (cf: `sudo yum -y install java-11-openjdk`), and configure `JAVA_HOME` environment variable.

Execute the following command to launch the server. The server will run indefinitely in the foreground. Press `ctrl+c` to stop.

```shell script
$JAVA_HOME/bin/java -version

sh bin/dukas-proxy
```

Search the log output for the following lines to confirm that the credentials are configured correctly.

```text
yyyy-MM-dd HH:mm:ss.SSS|INFO |...|Initializing application.
yyyy-MM-dd HH:mm:ss.SSS|INFO |...|IClient connecting... (url=http://platform.dukas.../jforex.jnlp,..., user=DemoUser, pass=MD5:37b...)
```

### REST API

From another terminal, query the REST endpoints to fetch the account data.

```shell script
curl -i 'http://localhost:65535/topic/account'

HTTP/1.1 200
Content-Type: application/json;charset=UTF-8

{
  "xt": 1234567890123,
  "xs": false,
  "ai": "0000000",
  "as": "OK",
  "ab": "1000000.0",
  "am": "0.0",
  "au": "DEMO000000000000"
}
```

List of subscribing instruments can be modified via HTTP `PATCH`/`DELETE` methods. 

```shell script
curl -i -X PATCH -H 'Content-Type: application/json' 'http://localhost:65535/subscription' -d '{"instruments":["USDJPY","EURJPY"]}'

HTTP/1.1 200
Content-Type: application/json;charset=UTF-8

{
  "instruments": ["USDJPY", "EURJPY"]
}
```

```shell script
curl -i -X DELETE -H 'Content-Type: application/json' 'http://localhost:65535/subscription' -d '{"instruments":["EURJPY"]}'

HTTP/1.1 200
Content-Type: application/json;charset=UTF-8

{
  "instruments": ["USDJPY"]
}
```

Subscribed instrument data can be fetched by HTTP `GET` method.

```shell script
curl -i 'http://localhost:65535/topic/tick/USDJPY'

HTTP/1.1 200
Content-Type: application/json;charset=UTF-8

{
  "xt": 1234567890123,
  "xs": false,
  "in": "USDJPY",
  "is": 3,
  "tt": 1234567890123,
  "ap": "108.819",
  "av": "0.3",
  "at": "12.34",
  "bp": "107.414",
  "bv": "0.1",
  "bt": "23.45"
}
```

The details for JSON object keys and values can be found in `com.after_sunrise.dukascopy.proxy.Subscriber.java` file.
* Timestamps are expressed in epoch-milliseconds.
* Floating-point numbers (Float, Double, BigDecimal) are expressed in String.

### WebSocket (STOMP)

WebSocket streaming interface can be accessed via [STOMP][stomp-home] sub-protocol (STOMP over WebSocket).
List of STOMP client libraries implemented in various programming lauguages (Java, JavaScript, Python, ...) can be found [here][stomp-impl].

* CONNECT : `ws://localhost:65535/stomp`
* SUBSCRIBE
  * Instruments : `/topic/subscription`
  * Account : `/topic/account`
  * Message : `/topic/message`
  * Bar : `/topic/bar`
  * Tick : `/topic/tick`
* SEND
  * Subscribe : `/subscription/create`
  * Unsubscribe : `/subscription/delete`

SEND payload shall be a JSON object (`application/json`) with the list of instrument names to modify. 
`id` can be optionally specified, which will be included as-is in the corresponding response message.
 
```json
{
  "id": "abc123",
  "instruments": ["USDJPY", "EURUSD"]
}
```

Refer to `com.after_sunrise.dukascopy.proxy.LauncherTest.java` for STOMP client usage examples.

## Bulding from Source

JDK 11 or later is required. Make sure the `JAVA_HOME` environment variable is configured.
Then Clone the repository, and use gradle(w) to build the archives.

```shell script
$JAVA_HOME/bin/java -version

git clone git@github.com:after-the-sunrise/dukas-proxy.git

cd dukas-proxy && ./gradlew clean build
```

The archives are generated under `./build/distributions/` directory.


[travis-page]:https://travis-ci.org/after-the-sunrise/dukas-proxy
[travis-icon]:https://travis-ci.org/after-the-sunrise/dukas-proxy.svg?branch=master
[github-releases]:https://github.com/after-the-sunrise/dukas-proxy/releases
[dukascopy-home]:https://www.dukascopy.com/
[dukascopy-wiki]:https://www.dukascopy.com/wiki/en/development
[stomp-home]:https://stomp.github.io/
[stomp-impl]:https://stomp.github.io/implementations.html

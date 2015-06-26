# JettyBoot
simple boot library of Jetty

```java
new JettyBoot(8090, "/harbor").asDevelopment().bootAwait();
```

No need to shutdown previous process when you restart it.  
Automatically shutdown before next process boot

# Quick Trial
Can boot it by example of LastaFlute:

1. prepare Java8 compile environment
2. clone https://github.com/dbflute-session/lastaflute-example-harbor
3. execute the main method of (org.docksidestage.boot) HarborBoot
4. access to http://localhost:8090/harbor

```java
public class HarborBoot {

    public static void main(String[] args) {
        new JettyBoot(8090, "/harbor").asDevelopment().bootAwait();
    }
}
```

Can login by user 'Pixy' and password 'sea', and can see debug log at console.

# Information
## Maven Dependency
```xml
<dependency>
    <groupId>org.dbflute.jetty</groupId>
    <artifactId>jetty-boot</artifactId>
    <version>0.3.2</version>
</dependency>
```

## License
Apache License 2.0

## Official site
comming soon...

# Thanks, Friends
JettyBoot is used by:  
comming soon...

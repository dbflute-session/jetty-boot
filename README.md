JettyBoot
=======================
simple boot library of Jetty

```java
new JettyBoot(8090, "/harbor").asDevelopment().bootAwait();
```

No need to shutdown previous process when you restart it.  
Automatically shutdown before next process boot

# Quick Trial
Can boot it by example of LastaFlute:

1. git clone https://github.com/dbflute-session/lastaflute-example-harbor.git
2. prepare database by *ReplaceSchema at DBFlute client directory 'dbflute_maihamadb'  
3. compile it by Java8, on e.g. Eclipse or IntelliJ or ... as Maven project
4. execute the *main() method of (org.docksidestage.boot) HarborBoot
5. access to http://localhost:8090/harbor  
and login by user 'Pixy' and password 'sea', and can see debug log at console.

*ReplaceSchema
```java
// call manage.sh at lastaflute-example-harbor/dbflute_maihamadb
// and select replace-schema in displayed menu
...$ sh manage.sh
```

*main() method
```java
public class HarborBoot {

    public static void main(String[] args) {
        new JettyBoot(8090, "/harbor").asDevelopment().bootAwait();
    }
}
```

# Information
## Maven Dependency in pom.xml
```xml
<dependency>
    <groupId>org.dbflute.jetty</groupId>
    <artifactId>jetty-boot</artifactId>
    <version>0.3.3</version>
</dependency>
```

## License
Apache License 2.0

## Official site
comming soon...

# Thanks, Friends
JettyBoot is used by:  
comming soon...

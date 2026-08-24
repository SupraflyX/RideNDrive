package com.routeshare;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

// just checks the spring context starts up without blowing up
@SpringBootTest
@ActiveProfiles("test")
class RouteShareApplicationTests {

    @Test
    void contextLoads() {
    }
}

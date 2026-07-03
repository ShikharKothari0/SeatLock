package com.ShikharKothari0.SeatLock.config;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

public class RedisTestContainer extends GenericContainer<RedisTestContainer> {
    private static final int REDIS_PORT = 6379;
    private static final RedisTestContainer INSTANCE =
            new RedisTestContainer(DockerImageName.parse("redis:7-alpine"));

    private RedisTestContainer(DockerImageName imageName) {
        super(imageName);
        withExposedPorts(REDIS_PORT);
    }

    public static RedisTestContainer getInstance() {
        return INSTANCE;
    }

    public int getMappedPort() {
        return getMappedPort(REDIS_PORT);

    }
}

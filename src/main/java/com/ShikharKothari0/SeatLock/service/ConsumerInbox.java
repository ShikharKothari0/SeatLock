package com.ShikharKothari0.SeatLock.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ConsumerInbox {
    private final JdbcTemplate jdbc;
    public ConsumerInbox(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean firstDelivery(String consumer, String messageId) {
        return jdbc.update("INSERT INTO consumed_message(consumer_name,message_id) VALUES (?,?) ON CONFLICT DO NOTHING", consumer, messageId) == 1;
    }
}

package com.uptimecrew.expense.kafka;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Header;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.support.ProducerListener;
import org.springframework.stereotype.Component;

@Component
public class TraceparentLoggingProducerListener implements ProducerListener<Object, Object> {

    private static final Logger LOG = LoggerFactory.getLogger(TraceparentLoggingProducerListener.class);

    @Override
    public void onSuccess(ProducerRecord<Object, Object> record,
                          org.apache.kafka.clients.producer.RecordMetadata recordMetadata) {
        Header header = record.headers().lastHeader("traceparent");
        if (header == null) {
            LOG.warn("outgoing kafka record has NO traceparent header topic={} key={}",
                     record.topic(), record.key());
            return;
        }
        LOG.info("outgoing traceparent={} topic={} key={}",
                 new String(header.value()), record.topic(), record.key());
    }
}

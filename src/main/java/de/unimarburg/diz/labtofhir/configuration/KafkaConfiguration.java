package de.unimarburg.diz.labtofhir.configuration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.StreamsBuilderFactoryBeanCustomizer;
import org.springframework.kafka.core.KafkaTemplate;

@Configuration
@EnableKafka
public class KafkaConfiguration {

    private static final Logger log = LoggerFactory.getLogger(KafkaConfiguration.class);
    @Autowired
    KafkaTemplate<String, Object> template;

    @Bean
    public StreamsBuilderFactoryBeanCustomizer streamsBuilderFactoryBeanCustomizer() {
        return factoryBean -> {
            factoryBean.setKafkaStreamsCustomizer(
                kafkaStreams -> kafkaStreams.setUncaughtExceptionHandler((t, e) -> {
                    log.error("Uncaught exception occured.", e);
                }));
        };
    }

//    @Bean
//    public ConcurrentKafkaListenerContainerFactory<Object, Object> kafkaListenerContainerFactory(
//        ConcurrentKafkaListenerContainerFactoryConfigurer configurer,
//        ConsumerFactory<Object, Object> kafkaConsumerFactory,
//        KafkaTemplate<Object, Object> template) {
//
//        ConcurrentKafkaListenerContainerFactory<Object, Object> factory = new ConcurrentKafkaListenerContainerFactory<>();
//        configurer.configure(factory, kafkaConsumerFactory);
//        factory.setErrorHandler(new SeekToCurrentErrorHandler(
//            new DeadLetterPublishingRecoverer(template)));
//
//        return factory;
//
//    }

}

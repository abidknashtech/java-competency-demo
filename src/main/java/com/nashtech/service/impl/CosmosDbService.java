package com.nashtech.service.impl;

import com.azure.cosmos.CosmosException;
import com.nashtech.service.CloudDataService;
import org.springframework.kafka.KafkaException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;
import com.nashtech.exception.DataNotFoundException;
import com.nashtech.model.Car;
import com.nashtech.model.CarBrand;
import com.nashtech.repository.CosmosDbRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;



@Service
@Slf4j
@Profile("cosmos")
public class CosmosDbService implements CloudDataService {
    /**
     * The reactive repository for {@link Car} entities
     * in Cosmos DB.
     * Used for performing CRUD operations and reactive data access.
     */
    @Autowired
    private CosmosDbRepository cosmosDbRepository;

    /**
     * The KafkaTemplate for sending vehicle data to Kafka topics.
     */
    @Autowired
    private  KafkaTemplate<String, Car> kafkaTemplate;


    /**
     * Sends the given {@link Car} object to the Kafka topic "myeventhub".
     * The method constructs a Kafka message
     * from the provided {@link Car} payload
     * and sends it using the configured {@link KafkaTemplate}.
     *
     * @param reactiveDataCar The {@link Car} object to be sent to Kafka.
     * @throws KafkaException
     * If an error occurs while sending the message to Kafka.
     */
    @Override
    public Mono<Void> pushData(final Car reactiveDataCar)  {
        try {
            Message<Car> message = MessageBuilder
                    .withPayload(reactiveDataCar)
                    .setHeader(KafkaHeaders.TOPIC, "myeventhub")
                    .build();
            kafkaTemplate.send(message);
        } catch (KafkaException kafkaException) {
            throw kafkaException;
        }
        return Mono.empty();
    }
    /**
     * Retrieves a Flux of cars with specified brand in reactive manner.
     * The Flux represents a stream of data that can be subscribed to for
     * continuous updates.
     *
     * @param brand The brand of cars to filter by.
     * @return A Flux of Car representing cars with the
     * specified brand.
     */
    public Flux<Car> getCarsByBrand(final String brand) {
        Flux<Car> allCarsOfBrand = cosmosDbRepository.getAllCarsByBrand(brand);
        return allCarsOfBrand
                .onErrorResume(CosmosException.class, error -> {
                    log.error("Error while retrieving data from Cosmos DB",
                                    error);
                    return Flux.error(new DataNotFoundException());
                })
                .doOnComplete(() -> log.info("Received Data Successfully"))
                .switchIfEmpty(Flux.error(new DataNotFoundException()));
    }

    /**
     * Retrieves a Flux of distinct car brands in a reactive manner.
     * The Flux represents a stream of data that can be subscribed to for
     * continuous updates.
     * This method also prints the distinct brands to the console for
     * demonstration purposes.
     *
     * @return A Flux of CarBrand representing distinct car brands.
     */
    public Flux<CarBrand> getAllBrands() {
        Flux<CarBrand> brandFlux =
                cosmosDbRepository.findDistinctBrands();
        return brandFlux
                .onErrorResume(CosmosException.class, error -> {
                    log.error(
                            "Error while retrieving data from Cosmos DB",
                            error);
                    return Flux.error(new DataNotFoundException());
                })
                .doOnComplete(() ->
                        log.info("Data processing completed."))
                .switchIfEmpty(Flux.error(new DataNotFoundException()));
    }
}

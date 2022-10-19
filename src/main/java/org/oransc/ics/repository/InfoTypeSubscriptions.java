/*-
 * ========================LICENSE_START=================================
 * O-RAN-SC
 * %%
 * Copyright (C) 2019 Nordix Foundation
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ========================LICENSE_END===================================
 */

package org.oransc.ics.repository;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.lang.invoke.MethodHandles;
import java.time.Duration;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Vector;
import java.util.function.Function;

import lombok.Builder;
import lombok.Getter;

import org.oransc.ics.configuration.ApplicationConfig;
import org.oransc.ics.datastore.DataStore;
import org.oransc.ics.exceptions.ServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

/**
 * Subscriptions of callbacks for type registrations
 */
@SuppressWarnings("squid:S2629") // Invoke method(s) only conditionally
@Configuration
public class InfoTypeSubscriptions {
    private final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
    private final Map<String, SubscriptionInfo> allSubscriptions = new HashMap<>();
    private final MultiMap<String, SubscriptionInfo> subscriptionsByOwner = new MultiMap<>();
    private final Gson gson = new GsonBuilder().create();
    private final Map<String, ConsumerCallbackHandler> callbackHandlers = new HashMap<>();
    private final DataStore dataStore;

    public interface ConsumerCallbackHandler {
        Mono<String> notifyTypeRegistered(InfoType type, SubscriptionInfo subscriptionInfo);

        Mono<String> notifyTypeRemoved(InfoType type, SubscriptionInfo subscriptionInfo);
    }

    @Builder
    @Getter
    public static class SubscriptionInfo {
        private String id;

        private String callbackUrl;

        private String owner;

        private String apiVersion;

        @Override
        public int hashCode() {
            return this.id.hashCode();
        }

        @Override
        public boolean equals(Object o) {
            if (o instanceof SubscriptionInfo) {
                return this.id.equals(((SubscriptionInfo) o).id);
            }
            return this.id.equals(o);
        }
    }

    public InfoTypeSubscriptions(@Autowired ApplicationConfig config) {
        this.dataStore = DataStore.create(config, "infotypesubscriptions");
        this.dataStore.createDataStore().subscribe();
    }

    public void registerCallbackhandler(ConsumerCallbackHandler handler, String apiVersion) {
        callbackHandlers.put(apiVersion, handler);
    }

    public synchronized void put(SubscriptionInfo subscription) {
        doPut(subscription);
        storeInFile(subscription);
        logger.debug("Added type status subscription {}", subscription.id);
    }

    public synchronized Collection<SubscriptionInfo> getAllSubscriptions() {
        return new Vector<>(allSubscriptions.values());
    }

    /**
     * Get a subscription and throw if not fond.
     *
     * @param id the ID of the subscription to get.
     * @return SubscriptionInfo
     * @throws ServiceException if not found
     */
    public synchronized SubscriptionInfo getSubscription(String id) throws ServiceException {
        SubscriptionInfo p = allSubscriptions.get(id);
        if (p == null) {
            throw new ServiceException("Could not find Information subscription: " + id, HttpStatus.NOT_FOUND);
        }
        return p;
    }

    /**
     * Get a subscription or return null if not found. Equivalent to get in all java
     * collections.
     *
     * @param id the ID of the subscription to get.
     * @return SubscriptionInfo
     */
    public synchronized SubscriptionInfo get(String id) {
        return allSubscriptions.get(id);
    }

    public synchronized int size() {
        return allSubscriptions.size();
    }

    public synchronized void clear() {
        allSubscriptions.clear();
        subscriptionsByOwner.clear();
        clearDatabase();
    }

    public void remove(SubscriptionInfo subscription) {
        allSubscriptions.remove(subscription.getId());
        subscriptionsByOwner.remove(subscription.owner, subscription);
        dataStore.deleteObject(getPath(subscription)).subscribe();

        logger.debug("Removed type status subscription {}", subscription.id);
    }

    /**
     * returns all subscriptions for an owner. The colllection can contain 0..n
     * subscriptions.
     *
     * @param owner
     * @return
     */
    public synchronized Collection<SubscriptionInfo> getSubscriptionsForOwner(String owner) {
        return subscriptionsByOwner.get(owner);
    }

    public synchronized void notifyTypeRegistered(InfoType type) {
        notifyAllSubscribers(
            subscription -> getCallbacksHandler(subscription.apiVersion).notifyTypeRegistered(type, subscription));
    }

    public synchronized void notifyTypeRemoved(InfoType type) {
        notifyAllSubscribers(
            subscription -> getCallbacksHandler(subscription.apiVersion).notifyTypeRemoved(type, subscription));
    }

    private ConsumerCallbackHandler getCallbacksHandler(String apiVersion) {
        ConsumerCallbackHandler callbackHandler = this.callbackHandlers.get(apiVersion);
        if (callbackHandler != null) {
            return callbackHandler;
        } else {
            return new ConsumerCallbackHandler() {
                @Override
                public Mono<String> notifyTypeRegistered(InfoType type, SubscriptionInfo subscriptionInfo) {
                    return error();
                }

                @Override
                public Mono<String> notifyTypeRemoved(InfoType type, SubscriptionInfo subscriptionInfo) {
                    return error();
                }

                private Mono<String> error() {
                    return Mono.error(new ServiceException(
                        "No notifyTypeRegistered handler found for interface version " + apiVersion,
                        HttpStatus.INTERNAL_SERVER_ERROR));
                }
            };
        }
    }

    private synchronized void notifyAllSubscribers(Function<? super SubscriptionInfo, Mono<String>> notifyFunc) {
        final int MAX_CONCURRENCY = 5;
        Flux.fromIterable(allSubscriptions.values()) //
            .flatMap(subscription -> notifySubscriber(notifyFunc, subscription), MAX_CONCURRENCY) //
            .subscribe();
    }

    /**
     * Invoking one consumer. If the call fails after retries, the subscription is
     * removed.
     *
     * @param notifyFunc
     * @param subscriptionInfo
     * @return
     */
    private Mono<String> notifySubscriber(Function<? super SubscriptionInfo, Mono<String>> notifyFunc,
        SubscriptionInfo subscriptionInfo) {
        Retry retrySpec = Retry.backoff(3, Duration.ofSeconds(1));
        return notifyFunc.apply(subscriptionInfo) //
            .retryWhen(retrySpec) //
            .onErrorResume(throwable -> {
                logger.warn("Consumer callback failed {}, removing subscription {}", throwable.getMessage(),
                    subscriptionInfo.id);
                this.remove(subscriptionInfo);
                return Mono.empty();
            }); //
    }

    private void clearDatabase() {
        this.dataStore.deleteAllData().blockLast();
    }

    private void storeInFile(SubscriptionInfo subscription) {
        String json = gson.toJson(subscription);
        byte[] bytes = json.getBytes();
        this.dataStore.writeObject(this.getPath(subscription), bytes)
            .doOnError(t -> logger.error("Could not store infotype subscription, reason: {}", t.getMessage())) //
            .subscribe();
    }

    public synchronized Flux<SubscriptionInfo> restoreFromDatabase() {
        return dataStore.listObjects("") //
            .flatMap(dataStore::readObject) //
            .map(this::toSubscriptionInfo) //
            .filter(Objects::nonNull) //
            .doOnNext(this::doPut);//
    }

    private SubscriptionInfo toSubscriptionInfo(byte[] bytes) {
        String json = new String(bytes);
        return gson.fromJson(json, SubscriptionInfo.class);
    }

    private void doPut(SubscriptionInfo subscription) {
        allSubscriptions.put(subscription.getId(), subscription);
        subscriptionsByOwner.put(subscription.owner, subscription);
    }

    private String getPath(SubscriptionInfo subscription) {
        return subscription.getId();
    }

}

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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * A map, where each key can be bound to may values
 */
public class MultiMap<K, V> {

    private final Map<K, Set<V>> map = new HashMap<>();

    public synchronized void put(K key, V value) {
        this.map.computeIfAbsent(key, k -> new HashSet<>()).add(value);
    }

    public synchronized void remove(String key, V id) {
        Set<V> innerMap = this.map.get(key);
        if (innerMap != null) {
            innerMap.remove(id);
            if (innerMap.isEmpty()) {
                this.map.remove(key);
            }
        }
    }

    public synchronized Collection<V> get(K key) {
        Set<V> innerMap = this.map.get(key);
        if (innerMap == null) {
            return Collections.emptyList();
        }
        Collection<V> result = new ArrayList<>(innerMap.size());
        result.addAll(innerMap);
        return result;
    }

    public synchronized void clear() {
        this.map.clear();
    }

}

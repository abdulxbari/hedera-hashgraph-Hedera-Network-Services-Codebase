package com.hedera.services.sysfiles.domain.throttling;

/*-
 * ‌
 * Hedera Services API Utilities
 * ​
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
 * ​
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
 * ‍
 */

import java.util.ArrayList;
import java.util.List;

import static java.util.stream.Collectors.toList;

/**
 * Throttle definitions POJO
 */
public class ThrottleDefinitions {
	List<ThrottleBucket> buckets = new ArrayList<>();

	/**
	 * list of throttle buckets
	 *
	 * @return throttle buckets
	 */
	public List<ThrottleBucket> getBuckets() {
		return buckets;
	}

	/**
	 * setter for the throttle buckets list
	 *
	 * @param buckets
	 * 		list of throttle buckets
	 */
	public void setBuckets(List<ThrottleBucket> buckets) {
		this.buckets = buckets;
	}

	/**
	 * Construct {@code ThrottleDefinitions } POJO from the protobuf {@code com.hederahashgraph.api.proto.java
	 * .ThrottleDefinitions}
	 *
	 * @param defs
	 * 		throttle definitions protobuf
	 * @return ThrottleDefinitions pojo
	 */
	public static ThrottleDefinitions fromProto(com.hederahashgraph.api.proto.java.ThrottleDefinitions defs) {
		var pojo = new ThrottleDefinitions();
		pojo.buckets.addAll(defs.getThrottleBucketsList().stream()
				.map(ThrottleBucket::fromProto)
				.collect(toList()));
		return pojo;
	}

	/**
	 * Construct {@code com.hederahashgraph.api.proto.java.ThrottleDefinitions } protobuf
	 *
	 * @return ThrottleDefinitions protobuf
	 */
	public com.hederahashgraph.api.proto.java.ThrottleDefinitions toProto() {
		return com.hederahashgraph.api.proto.java.ThrottleDefinitions.newBuilder()
				.addAllThrottleBuckets(buckets.stream().map(ThrottleBucket::toProto).collect(toList()))
				.build();
	}
}

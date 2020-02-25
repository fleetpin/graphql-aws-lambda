package com.fleetpin.graphql.aws.lambda.model;

import org.reactivestreams.Publisher;

import com.fleetpin.graphql.builder.annotations.Entity;
import com.fleetpin.graphql.builder.annotations.Subscription;

@Entity
public class Example {

	String name;
	
	public String getName() {
		return name;
	}
	
	@Subscription
	public static Publisher<Example> allExamples() {
		return null;
	}
}

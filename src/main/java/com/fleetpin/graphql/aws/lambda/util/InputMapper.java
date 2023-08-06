package com.fleetpin.graphql.aws.lambda.util;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;


import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;
import com.fleetpin.graphql.builder.annotations.GraphQLIgnore;

public class InputMapper {
	
	
	public static final ObjectMapper MAPPER = new ObjectMapper()
			.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
			.registerModule(new ParameterNamesModule())
			.registerModule(new Jdk8Module())
			.registerModule(new JavaTimeModule())
			.disable(DeserializationFeature.READ_DATE_TIMESTAMPS_AS_NANOSECONDS)
			.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
			.disable(SerializationFeature.WRITE_DATE_TIMESTAMPS_AS_NANOSECONDS)
			.disable(SerializationFeature.WRITE_DURATIONS_AS_TIMESTAMPS)
			.setVisibility(PropertyAccessor.FIELD, Visibility.ANY);

	public static void assign(Object existing, Object input) {
		try {
			Class<?> existingClass = existing.getClass();
			Class<?> inputClass = input.getClass();

			for(var inputField: getFields(inputClass)) {
				inputField.setAccessible(true);
				var obj = inputField.get(input);
				var type = inputField.getType();
				var generic = inputField.getGenericType();
				if(obj != null) {
					var existingField = getField(existingClass, inputField.getName());
					if(existingField == null) {
						continue;
					}
					if(Modifier.isStatic(inputField.getModifiers()) || Modifier.isStatic(existingField.getModifiers())) {
						continue;
					}
					if(Modifier.isFinal(inputField.getModifiers()) || Modifier.isFinal(existingField.getModifiers())) {
						continue;
					}
					if(obj instanceof Optional) {
						obj = ((Optional<?>) obj).orElse(null);
						var t = ((ParameterizedType) generic).getActualTypeArguments()[0];
						if(t instanceof Class) {
							type = (Class<?>) t;
						}else {
							type = (Class<?>) ((ParameterizedType) t).getRawType();
							generic = t;
						}
					}
					existingField.setAccessible(true);

					if(obj instanceof Collection) {
						ParameterizedType existingType = (ParameterizedType) existingField.getGenericType();
						Class existingInnerClass = (Class) existingType.getActualTypeArguments()[0];
						
						ParameterizedType inputType = (ParameterizedType) generic;
						Class inputInnerClass = (Class) inputType.getActualTypeArguments()[0];
						if(existingInnerClass.isAssignableFrom(inputInnerClass)) {
							existingField.set(existing, obj);
						}else {
							Collection toSet = new ArrayList<>();
							
							for(Object o: (Collection) obj) {
								Object build = existingInnerClass.getConstructor().newInstance();
								assign(build, o);
								toSet.add(build);
							}
							existingField.set(existing, toSet);
						}
						continue;
					}
					

					
					
					if(existingField.getType().isAssignableFrom(type)) {
						existingField.set(existing, obj);
					}else {
						var existingObj = existingField.get(existing);
						if(existingObj == null) {
							existingObj = existingField.getType().getConstructor().newInstance();
							existingField.set(existing, existingObj);
						}
						assign(existingObj, obj);
					}
				}
			}

			//check nothing returns null that shouldn't, is just a best effort
			for(Method method: existingClass.getMethods()) {
				if(method.getDeclaringClass().equals(Object.class)) {
					continue;
				}
				if(method.isAnnotationPresent(GraphQLIgnore.class)) {
					continue;
				}
				if(Modifier.isStatic(method.getModifiers())) {
					continue;
				}
				if(method.getParameterCount() != 0) {
					continue;
				}
				//database deals to these
				if(method.getName().equals("getCreatedAt") || method.getName().equals("getUpdatedAt")) {
					continue;
				}
				if(!method.getName().matches("(get|is)[A-Z].*")) {
					continue;
				}
				String name;
				if(method.getName().startsWith("get")) {
					name = method.getName().substring("get".length(), "get".length() + 1).toLowerCase() + method.getName().substring("get".length() + 1);
				}else {
					name = method.getName().substring("is".length(), "is".length() + 1).toLowerCase() + method.getName().substring("is".length() + 1);
				}
				if(method.invoke(existing) == null) {
					throw new RuntimeException("Missing required type " + name);
				}
			}

		}catch (ReflectiveOperationException e) {
			throw new RuntimeException(e);
		}
	}

	private static Field getField(Class existingClass, String name) {
		for(var field: getFields(existingClass)) {
			if(field.getName().equals(name)) {
				return field;
			}
		}
		return null;
	}

	private static List<Field> getFields(Class<?> inputClass) {
		List<Field> fields = new ArrayList<>();
		while(inputClass != null) {
			fields.addAll(Arrays.asList(inputClass.getDeclaredFields()));
			inputClass = inputClass.getSuperclass();
		}
		return fields;
	}

}

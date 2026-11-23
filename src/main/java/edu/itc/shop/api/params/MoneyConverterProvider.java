package edu.itc.shop.api.params;

import edu.itc.shop.domain.Money;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.ext.ParamConverter;
import jakarta.ws.rs.ext.ParamConverterProvider;
import jakarta.ws.rs.ext.Provider;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;

@Provider
public class MoneyConverterProvider implements ParamConverterProvider {

    @Override
    public <T> ParamConverter<T> getConverter(Class<T> rawType, Type genericType, Annotation[] annotations) {
        if (rawType != Money.class)
            return null; // only handle Money

        return new ParamConverter<T>() {
            @Override
            public T fromString(String value) {
                if (value == null)
                    return null;
                try {
                    return rawType.cast(Money.parse(value));
                } catch (IllegalArgumentException e) {
                    throw new BadRequestException(e.getMessage()); // -> 400
                }
            }

            @Override
            public String toString(T value) {
                return value == null ? null : value.toString();
            }
        };
    }
}
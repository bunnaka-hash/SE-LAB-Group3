package edu.itc.shop.api.params;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.ext.ParamConverter;
import jakarta.ws.rs.ext.ParamConverterProvider;
import jakarta.ws.rs.ext.Provider;
import edu.itc.shop.domain.Money;

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
                    // Jersey turns a plain failure into 404; a WebApplicationException passes
                    // through
                    throw new BadRequestException(e.getMessage());
                }
            }

            @Override
            public String toString(T value) {
                return value == null ? null : value.toString();
            }
        };
    }
}
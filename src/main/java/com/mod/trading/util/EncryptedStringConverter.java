package com.mod.trading.util;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Lazy;

/**
 * JPA converter that encrypts a String column on write and decrypts on read.
 * Apply with @Convert(converter = EncryptedStringConverter.class) on entity fields.
 *
 * NOTE: Uses ApplicationContext to access EncryptionService because JPA
 * instantiates Converters outside the Spring container.
 */
@Converter
public class EncryptedStringConverter implements AttributeConverter<String, String> {

    private static ApplicationContext applicationContext;

    @Autowired
    public void setApplicationContext(@Lazy ApplicationContext context) {
        EncryptedStringConverter.applicationContext = context;
    }

    private EncryptionService getService() {
        if (applicationContext == null) {
            throw new IllegalStateException("ApplicationContext not initialized");
        }
        return applicationContext.getBean(EncryptionService.class);
    }

    @Override
    public String convertToDatabaseColumn(String attribute) {
        if (attribute == null) return null;
        return getService().encrypt(attribute);
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        if (dbData == null) return null;
        return getService().decrypt(dbData);
    }
}

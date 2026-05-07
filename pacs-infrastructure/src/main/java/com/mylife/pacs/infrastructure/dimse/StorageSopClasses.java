package com.mylife.pacs.infrastructure.dimse;

import org.dcm4che3.data.UID;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;

public final class StorageSopClasses {

    private StorageSopClasses() {
    }

    public static String[] all() {
        return Arrays.stream(UID.class.getFields())
                .filter(StorageSopClasses::isPublicStringField)
                .filter(field -> field.getName().contains("Storage"))
                .filter(field -> !"Storage".equals(field.getName()))
                .filter(field -> !field.getName().startsWith("MediaStorage"))
                .filter(field -> !field.getName().startsWith("StorageCommitment"))
                .map(StorageSopClasses::valueOf)
                .filter(value -> value != null && !value.isBlank())
                .distinct()
                .toArray(String[]::new);
    }

    private static boolean isPublicStringField(Field field) {
        return field.getType() == String.class && Modifier.isStatic(field.getModifiers()) && Modifier.isPublic(field.getModifiers());
    }

    private static String valueOf(Field field) {
        try {
            return (String) field.get(null);
        } catch (IllegalAccessException exception) {
            throw new IllegalStateException("Failed to read SOP class UID: " + field.getName(), exception);
        }
    }
}

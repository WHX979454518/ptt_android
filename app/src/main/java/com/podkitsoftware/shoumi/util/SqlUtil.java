package com.podkitsoftware.shoumi.util;

import org.apache.commons.lang3.StringUtils;

import java.util.Collection;

public class SqlUtil {
    public static String toSqlSet(final Collection<?> values) {
        if (values.isEmpty()) {
            return "()";
        }

        return "('" + StringUtils.join(values, "','") + "')";
    }

    public static String toSqlSet(final String[] values) {
        if (values == null || values.length == 0) {
            return "()";
        }

        return "('" + StringUtils.join(values, "','") + "')";
    }

    public static String toSqlSet(final long[] values) {
        if (values == null || values.length == 0) {
            return "()";
        }

        return "('" + StringUtils.join(values, "','") + "')";
    }
}

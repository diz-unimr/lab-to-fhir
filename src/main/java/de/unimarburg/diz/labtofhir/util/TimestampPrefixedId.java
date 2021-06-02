package de.unimarburg.diz.labtofhir.util;

/*
 * (C) Copyright IBM Corp. 2020
 *
 * SPDX-License-Identifier: Apache-2.0
 */

import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.r4.model.DateTimeType;

/**
 * Provides identity strings using a provided unique part but prefixed with an encoded time string
 * to improve database locality when used in b-tree indexes.
 * <p>
 * See https://ibm.github.io/FHIR/guides/FHIRPerformanceGuide/#51-logical-id-generation
 */
public class TimestampPrefixedId {

    private static final int DEFAULT_MAX_LENGTH = 64;

    public static String createNewIdentityValue(DateTimeType dateTimeType, String uniquePart,
        int maxLength) {
        // It's OK to use milli-time here. It doesn't matter too much if the time changes
        // because we're not using the timestamp to determine uniqueness in any way. The
        // timestamp prefix is purely to help push index writes to the right hand side
        // of the btree, minimizing the number of physical reads likely required
        // during ingestion when an index is too large to be fully cached.
        //
        // String encoding. Needs to collate correctly, so don't use any
        // byte-based encoding which would be sensitive to endian issues. For simplicity,
        // hex is sufficient, although a custom encoding using the full character set
        // supported by FHIR identifiers would be a little more compact (== smaller indexes).
        // Do not use Base64.

        String result;
        if (dateTimeType.hasValue()) {

            var prefix = Long.toHexString(dateTimeType.getValue()
                .getTime());

            result = prefix + "-" // redundant, but more visually appealing.
                + uniquePart;
        } else {
            result = uniquePart;
        }

        // Truncate to max length
        return StringUtils.truncate(result, maxLength);
    }

    public static String createNewIdentityValue(DateTimeType dateTimeType, String uniquePart) {
        return createNewIdentityValue(dateTimeType, uniquePart, DEFAULT_MAX_LENGTH);
    }
}
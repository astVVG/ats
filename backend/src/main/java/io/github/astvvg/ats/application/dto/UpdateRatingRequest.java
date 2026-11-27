package io.github.astvvg.ats.application.dto;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.io.IOException;

@Schema(description = "An empty body {} is the same as a null rating and removes it.")
public record UpdateRatingRequest(
        @Schema(
                        nullable = true,
                        minimum = "1",
                        maximum = "5",
                        example = "4",
                        description =
                                "Whole number from 1 to 5; null or omitted removes the rating."
                                        + " Decimals and strings are rejected, never truncated.")
                @JsonDeserialize(using = UpdateRatingRequest.WholeNumber.class)
                @Min(1)
                @Max(5)
                Integer rating) {

    /**
     * Accepts only a JSON integer that fits an int. Jackson would otherwise truncate 3.5 to 3 and
     * coerce "3" to 3; this applies to this field only, not to the global mapper.
     */
    public static final class WholeNumber extends StdDeserializer<Integer> {

        public WholeNumber() {
            super(Integer.class);
        }

        @Override
        public Integer deserialize(JsonParser parser, DeserializationContext context)
                throws IOException {
            if (parser.currentToken() == JsonToken.VALUE_NUMBER_INT
                    && parser.getNumberType() == JsonParser.NumberType.INT) {
                return parser.getIntValue();
            }
            // Same exception type as other wrongly typed values, so the field is reported.
            throw InvalidFormatException.from(
                    parser, "rating must be a whole number", parser.getText(), Integer.class);
        }
    }
}

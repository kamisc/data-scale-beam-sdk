package com.sewerynkamil.scale;

import com.sewerynkamil.fxscraping.Currency;
import org.apache.beam.sdk.Pipeline;
import org.apache.beam.sdk.io.TextIO;
import org.apache.beam.sdk.options.PipelineOptions;
import org.apache.beam.sdk.options.PipelineOptionsFactory;
import org.apache.beam.sdk.transforms.Filter;
import org.apache.beam.sdk.transforms.MapElements;
import org.apache.beam.sdk.transforms.Regex;
import org.apache.beam.sdk.transforms.ToString;
import org.apache.beam.sdk.values.TypeDescriptor;
import org.apache.beam.sdk.values.TypeDescriptors;

import java.time.YearMonth;
import java.util.Map;

import static com.sewerynkamil.scale.DataCleaning.*;

public class BeamApp {
    public static void main(String[] args) {
        YearMonth ym = YearMonth.of(2020, 4);
        DataTransformation dt = new DataTransformation(ym, Currency.GBP);
        String fileName = "data/sales-global.dat";

        TypeDescriptor<Map<String, String>> dataType = TypeDescriptors.maps(TypeDescriptors.strings(), TypeDescriptors.strings());

        PipelineOptions options = PipelineOptionsFactory.fromArgs(args).withValidation().create();
        Pipeline pipeline = Pipeline.create(options);

        pipeline
                .apply("Read file", TextIO.read().from(fileName))
                .apply("Apply regex and filter", Regex.allMatches(saleTransactionRegex))
                .apply("Make a map", MapElements.into(dataType).via(composeHashMap))
                .apply("Proper nulls", MapElements.into(dataType).via(properNulls))
                .apply("Validate offer/discount", Filter.by(discountAndOfferOnlyWithUserId))
                .apply("Standardize data values", MapElements.into(dataType).via(dt.standardizeDate))
                .apply("Convert prices to GBP", MapElements.into(dataType).via(dt.fxConvertPrice))
                .apply("Enrich with weather data", MapElements.into(dataType).via(dt.enrichWithWeatherData))
                .apply("Temperature value scaling", MapElements.into(dataType).via(dt.minMaxScalingTemperature))
                .apply("One-hot encode type", MapElements.into(dataType).via(dt.oneHotEncodeType))
                .apply("One-hot encode size", MapElements.into(dataType).via(dt.oneHotEncodeSize))
                .apply("Stringify", ToString.elements())
                .apply("Write to file", TextIO.write().withoutSharding().to("data/sales-global-beamsdk.dat"));

        pipeline.run();
    }
}

package com.sewerynkamil.scale;

import com.sewerynkamil.fxscraping.Currency;
import com.sewerynkamil.fxscraping.FxHelper;
import com.sewerynkamil.model.SaleTransaction;
import com.sewerynkamil.weather.WeatherHelper;
import com.sewerynkamil.weather.WeatherLocation;
import org.apache.beam.sdk.transforms.SerializableFunction;

import java.io.Serializable;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DataTransformation implements Serializable {
    private FxHelper fxHelper;
    private WeatherHelper weatherHelper;
    private double tempDiff;

    public DataTransformation(YearMonth ym, Currency baseCurrency) {
        fxHelper = new FxHelper(ym, baseCurrency);
        weatherHelper = new WeatherHelper(ym);
        tempDiff = weatherHelper.getMaxTemp() - weatherHelper.getMinTemp();
    }

    public final SerializableFunction<Map<String, String>, Map<String, String>> standardizeDate = transactionMap -> {
        DateTimeFormatter formatter = switch (SaleTransaction.Country.valueOf(transactionMap.get("country"))) {
            case UK -> DateTimeFormatter.ofPattern("d['st']['nd']['rd']['th'] MMM yyyy HH:mm:ss", Locale.UK);
            case ITALY -> DateTimeFormatter.ofPattern("d/M/yyyy HH:mm:ss", Locale.ITALY);
            case JAPAN -> DateTimeFormatter.ofPattern("yyyy年M月d日 HH時mm分ss秒", Locale.JAPAN);
            case CANADA -> DateTimeFormatter.ofPattern("M/d/yyyy HH:mm:ss", Locale.CANADA);
        };

        LocalDateTime dt = LocalDateTime.parse(transactionMap.get("timestamp"), formatter);
        Map<String, String> newTransactionMap = new HashMap<>(transactionMap);
        newTransactionMap.put("timestamp", dt.format(DateTimeFormatter.ISO_DATE_TIME));
        return newTransactionMap;
    };

    public final SerializableFunction<Map<String, String>, Map<String, String>> fxConvertPrice =
            new SerializableFunction<Map<String, String>, Map<String, String>>() {
                @Override
                public Map<String, String> apply(Map<String, String> transactionMap) {
                    String regex = null;
                    LocalDate fxDate = LocalDateTime.parse(transactionMap.get("timestamp"), DateTimeFormatter.ISO_DATE_TIME).toLocalDate();
                    double fxRate = 0.0;

                    switch (SaleTransaction.Country.valueOf(transactionMap.get("country"))) {
                        case UK -> {
                            regex = "£(\\d+\\.\\d{2})";
                            fxRate = 1.0;
                        }
                        case ITALY -> {
                            regex = "€(\\d+(\\.\\d{1,2})?)";
                            fxRate = getFxHelper().getRate(fxDate, Currency.EUR);
                        }
                        case JAPAN -> {
                            regex = "¥(\\d+)";
                            fxRate = getFxHelper().getRate(fxDate, Currency.JPY);
                        }
                        case CANADA -> {
                            regex = "CAD\\$(\\d+\\.\\d{2})";
                            fxRate = getFxHelper().getRate(fxDate, Currency.CAD);
                        }
                    }

                    Matcher matcher = Pattern.compile(regex).matcher(transactionMap.get("price"));
                    if (!matcher.matches()) {
                        throw new IllegalArgumentException("Invalid price value");
                    }

                    Double d = Double.parseDouble(matcher.group(1)) * fxRate;
                    Map<String, String> newTransactionMap = new HashMap<>(transactionMap);

                    return newTransactionMap;
                }
            };

    public final SerializableFunction<Map<String, String>, Map<String, String>> enrichWithWeatherData =
            new SerializableFunction<Map<String,String>, Map<String,String>>() {
                @Override
                public Map<String, String> apply(Map<String, String> transactionMap) {
                    LocalDate weatherDate = LocalDateTime.parse(transactionMap.get("timestamp"), DateTimeFormatter.ISO_DATE_TIME).toLocalDate();
                    String city = transactionMap.get("city").toUpperCase();
                    city = Normalizer.normalize(city, Normalizer.Form.NFD).replaceAll("\\p{InCombiningDiacriticalMarks}+", "");

                    String countryShort = switch (city) {
                        case "TURIN" -> "IT";
                        case "TOKYO" -> "JP";
                        case "LONDON" -> "GB";
                        case "MONTREAL" -> "CA";
                        default -> "";
                    };

                    WeatherLocation location = WeatherLocation.valueOf(countryShort);
                    Double temp = weatherHelper.getWeather(weatherDate, location).getTemperature();
                    Map<String, String> newTransactionMap = new HashMap<>(transactionMap);
                    newTransactionMap.put("temperature", temp.toString());
                    return newTransactionMap;
                }
            };

    public final SerializableFunction<Map<String, String>, Map<String, String>> minMaxScalingTemperature =
            new SerializableFunction<Map<String,String>, Map<String,String>>() {
                @Override
                public Map<String, String> apply(Map<String, String> transactionMap) {
                    Double temp = Double.parseDouble(transactionMap.get("temperature"));
                    temp = (temp - getWeatherHelper().getMinTemp()) / tempDiff;
                    Map<String, String> newTransactionMap = new HashMap<>(transactionMap);
                    newTransactionMap.put("temperature", temp.toString());
                    return transactionMap;
                }
            };

    public final SerializableFunction<Map<String, String>, Map<String, String>> oneHotEncodeType = transactionMap -> {
        int[] typeVector = {0, 0, 0, 0};
        int idx = switch (transactionMap.get("type")) {
            case "italian", "espresso", "イタリア" -> 0;
            case "brazilian", "ブラジル" -> 1;
            case "colombian", "コロンビア" -> 2;
            case "blend", "ブレンド" -> 3;
            default -> throw new IllegalArgumentException("Invalid coffee type");
        };
        typeVector[idx] = 1;
        Map<String, String> newTransactionMap = new HashMap<>(transactionMap);
        newTransactionMap.put("type", Arrays.toString(typeVector));
        return newTransactionMap;
    };

    public final SerializableFunction<Map<String, String>, Map<String, String>> oneHotEncodeSize = transactionMap -> {
        int[] sizeVector = {0, 0, 0, 0};
        int idx = switch (transactionMap.get("size").toString()) {
            case "GG", "XL", "超大" -> 0;
            case "G", "L", "大" -> 1;
            case "M", "中" -> 2;
            case "P", "S", "小" -> 3;
            default -> throw new IllegalStateException("Invalid coffee size");
        };
        sizeVector[idx] = 1;
        Map<String, String> newTransactionMap = new HashMap<>(transactionMap);
        newTransactionMap.put("size", Arrays.toString(sizeVector));
        return newTransactionMap;
    };

    public FxHelper getFxHelper() {
        return fxHelper;
    }

    public WeatherHelper getWeatherHelper() {
        return weatherHelper;
    }
}

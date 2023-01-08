package com.sewerynkamil.scale;

import org.apache.beam.sdk.transforms.SerializableFunction;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public interface DataCleaning {
    Pattern saleTransactionRegex = Pattern.compile(
            "SaleTransaction\\{uuid='?([A-Fa-f0-9\\-]+)'?,timestamp='(.*)',type='?(.*?)'?,size='?(.{1,3}?)'?,price='?(.*?)'?,offer='?(.*?)'?,discount='?(.*?)'?,userId=(\\d*),country='?(.*?)'?,city='?(.*?)'?}"
    );

    /**
     * Prepare map from passed transactions.
     */
    SerializableFunction<List<String>, Map<String, String>> composeHashMap = groups -> {
        Map<String, String> map = new HashMap<>();
        try {
            map.put("uuid", groups.get(1));
            map.put("timestamp", groups.get(2));
            map.put("type", groups.get(3));
            map.put("size", groups.get(4));
            map.put("price", groups.get(5));
            map.put("offer", groups.get(6));
            map.put("discount", groups.get(7));
            map.put("userId", groups.get(8));
            map.put("country", groups.get(9));
            map.put("city", groups.get(10));
        } catch (Exception e) {
            System.out.println("Groups = " + groups);
        }
        return map;
    };

    /**
     * Remove transactions without offer and discount.
     */
    SerializableFunction<Map<String, String>, Map<String, String>> properNulls = sale -> {
        Map<String, String> newSaleMap = new HashMap<>(sale);
        String offer = sale.get("offer");
        if (offer != null && (offer.isBlank() || offer.equals("null"))) {
            newSaleMap.remove("offer");
        }
        String discount = sale.get("discount");
        if (discount != null && (discount.isBlank() || discount.equals("null"))) {
            newSaleMap.remove("discount");
        }
        return newSaleMap;
    };

    /**
     * Check if transaction has discount or offer and userId.
     */
    SerializableFunction<Map<String, String>, Boolean> discountAndOfferOnlyWithUserId = sale ->
        !((sale.get("discount") != null || sale.get("offer") != null) && Long.parseLong(sale.get("userId")) <= 0);
}

package com.sewerynkamil.weather;

import java.io.Serializable;

public class WeatherData implements Serializable {
    private double temperature;
    private double precipitation;
    private double pressure;

    public WeatherData(double temperature, double precipitation, double pressure) {
        this.temperature = temperature;
        this.precipitation = precipitation;
        this.pressure = pressure;
    }

    public double getTemperature() {
        return temperature;
    }

    public double getPrecipitation() {
        return precipitation;
    }

    public double getPressure() {
        return pressure;
    }

    @Override
    public String toString() {
        return "WeatherData{" +
                temperature +
                "°C, " + precipitation +
                "mm, " + pressure +
                "hPa}";
    }
}

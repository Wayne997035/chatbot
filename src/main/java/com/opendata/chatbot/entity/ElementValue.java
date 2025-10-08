package com.opendata.chatbot.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import org.springframework.stereotype.Component;

@Component
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class ElementValue {
//    private String value;
//    private String measures;

    @JsonProperty("Temperature")
    private String temperature;

    @JsonProperty("DewPoint")
    private String dewPoint;

    @JsonProperty("RelativeHumidity")
    private String relativeHumidity;

    @JsonProperty("ApparentTemperature")
    private String apparentTemperature;

    @JsonProperty("ComfortIndex")
    private String comfortIndex;

    @JsonProperty("ComfortIndexDescription")
    private String comfortIndexDescription;

    @JsonProperty("WindSpeed")
    private String windSpeed;

    @JsonProperty("BeaufortScale")
    private String beaufortScale;

    @JsonProperty("WindDirection")
    private String windDirection;

    @JsonProperty("ProbabilityOfPrecipitation")
    private String probabilityOfPrecipitation;

    @JsonProperty("Weather")
    private String weather;

    @JsonProperty("WeatherCode")
    private String weatherCode;

    @JsonProperty("WeatherDescription")
    private String weatherDescription;
}

package com.opendata.chatbot.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class Locations {

    @JsonProperty("DatasetDescription")
    public String datasetDescription;

    @JsonProperty("LocationsName")
    private String locationsName;

    @JsonProperty("Dataid")
    public String dataid;

    @JsonProperty("Location")
    private List<Location> location;

//    private List<Location> location;
}

package com.opendata.chatbot.service.impl;

import com.opendata.chatbot.dao.WeatherForecastDto;
import com.opendata.chatbot.entity.*;
import com.opendata.chatbot.repository.OpenDataRepo;
import com.opendata.chatbot.service.OpenDataCwb;
import com.opendata.chatbot.util.JsonConverter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@Slf4j
@RequiredArgsConstructor
public class OpenDataCwbImpl implements OpenDataCwb {

    @Value("${spring.boot.openCWB.cwbUrl}")
    private String cwbUrl;

    private final OpenDataRepo openDataRepo;

//    @Autowired
//    private RabbitTemplate rabbitTemplate;

    private final RestTemplate restTemplate;

    @Override
    public String AllData(String url) {
        String body = null;
        try {
            body = restTemplate.getForEntity(URI.create(url), String.class).getBody();
        } catch (Exception e) {
            log.error("Base64 decode Error: {}", e.getMessage(), e);
        }
        return body;
    }

    @Override
    public void cityCwb() {

        var urlTemplate = new String(Base64.getDecoder().decode(cwbUrl), StandardCharsets.UTF_8);

        for (int i = 1; i <= 87; i += 2) {
//            if (stopSync) break; // 一旦撞時間就停止整個任務
            var s = String.format("%03d", i);
            var openDataCwbUrl = replaceVariable(urlTemplate, new String[]{s});

            Center center;
            try {
                Thread.sleep(2000); // API 限速
                center = JsonConverter.toObject(AllData(openDataCwbUrl), Center.class);
            } catch (InterruptedException e) {
                log.error("Thread sleep interrupted", e);
                continue;
            }

//            log.info("center Job City = {}", center);
            if (center == null || center.getRecords() == null) continue;

            var locationsList = center.getRecords().getLocations(); // List<Locations>
            if (locationsList.isEmpty()) continue;

            for (var locs : locationsList) {
//                if (stopSync) break; // 一旦撞時間就停止
                String city = locs.getLocationsName() != null ? locs.getLocationsName().replace("臺", "台") : "未知縣市";
                log.info("Job City = {}", city);

                if (locs.getLocation() != null) {
                    var locationList = new ArrayList<Location>();
                    for (var loc : locs.getLocation()) {
                        var location = new Location();
                        location.setLocationName(loc.getLocationName());
                        location.setGeocode(loc.getGeocode());
                        location.setLatitude(loc.getLatitude());
                        location.setLongitude(loc.getLongitude());
                        location.setWeatherElement(loc.getWeatherElement());
                        locationList.add(location);
                    }
                    weatherForecast(city, locationList);
//                    if (stopSync) break; // 一旦撞時間就停止
                }
            }
        }
    }






    @Override
    public String replaceVariable(String url, String[] vars) {
        int countVarInUrl = 0;
        String urlChk = url;

        while (urlChk.contains("$")) {
            int pos = urlChk.indexOf("$");
            urlChk = urlChk.substring(pos + 1);
            countVarInUrl++;
        }
        if (countVarInUrl != vars.length) {
            log.error("openData Url Replace Error");
        }
        for (String var : vars) {
            url = url.replaceFirst("\\$", var);
        }
        return url;
    }


    @Override
    public void weatherForecast(String locationsName, List<Location> locationList) {
        String city = locationsName != null ? locationsName.replace("臺", "台") : "未知城市";

        if (locationList == null || locationList.isEmpty()) {
            log.warn("City {} has empty location list, nothing to update.", city);
            return;
        }

        Map<String, String> elementMapping = new HashMap<>();
        elementMapping.put("平均溫度", "T");
        elementMapping.put("平均露點溫度", "Td");
        elementMapping.put("平均相對濕度", "RH");
        elementMapping.put("風速", "WS");
        elementMapping.put("風向", "WD");
        elementMapping.put("12小時降雨機率", "PoP12h");
        elementMapping.put("6小時降雨機率", "PoP6h");
        elementMapping.put("天氣現象", "Wx");
        elementMapping.put("天氣預報綜合描述", "WeatherDescription");
        elementMapping.put("紫外線指數", "CI");
        elementMapping.put("體感溫度", "AT");

        for (var location : locationList) {
            String district = location.getLocationName();
            if (district == null) {
                log.warn("City {} has a location with null district, skipped.", city);
                continue;
            }

            if (location.getWeatherElement() == null || location.getWeatherElement().isEmpty()) {
                log.warn("City {}, district {} has no weather elements, skipped.", city, district);
                continue;
            }

            var weatherForecastList = new ArrayList<WeatherForecast>();

            for (var weatherElement : location.getWeatherElement()) {
                if (weatherElement.getTime() == null || weatherElement.getTime().isEmpty()) continue;

                // 找最接近當下時間的 Time
                Time closestTime = weatherElement.getTime().stream()
                        .min((t1, t2) -> Long.compare(
                                Math.abs(parseTimeSafe(t1.getStartTime()) - System.currentTimeMillis() / 1000),
                                Math.abs(parseTimeSafe(t2.getStartTime()) - System.currentTimeMillis() / 1000)
                        ))
                        .orElse(null);

                if (closestTime == null || closestTime.getElementValue() == null || closestTime.getElementValue().isEmpty()) {
                    log.warn("City {}, district {}, element {} has no valid element value, skipped.", city, district, weatherElement.getElementName());
                    continue;
                }

                // 先比對 DB
//                var existing = openDataRepo.findByDistrictAndCity(district, city);
//                if (existing != null && existing.getWeatherForecast() != null) {
//                    boolean alreadyExists = existing.getWeatherForecast().stream()
//                            .anyMatch(wf -> wf.getStartTime().equals(closestTime.getStartTime()));
//                    if (alreadyExists) {
//                        log.info("City {}, district {} startTime {} already exists, stop syncing.", city, district, closestTime.getStartTime());
////                        stopSync = true; // 設置 flag
//                        return; // 直接停止整個方法
//                    }
//                }

                // 取第一筆有值的 ElementValue
                ElementValue ev = closestTime.getElementValue().stream()
                        .filter(this::hasAnyValue)
                        .findFirst()
                        .orElse(null);

                if (ev == null) continue;

                WeatherForecast wf = new WeatherForecast();
                wf.setElementName(elementMapping.getOrDefault(weatherElement.getElementName(), weatherElement.getElementName()));
                wf.setDescription(weatherElement.getElementName());
                wf.setStartTime(closestTime.getStartTime());

                // 塞 value，根據哪個欄位有值
                if (ev.getTemperature() != null) wf.setValue(ev.getTemperature());
                else if (ev.getDewPoint() != null) wf.setValue(ev.getDewPoint());
                else if (ev.getRelativeHumidity() != null) wf.setValue(ev.getRelativeHumidity());
                else if (ev.getApparentTemperature() != null) wf.setValue(ev.getApparentTemperature());
                else if (ev.getComfortIndex() != null) wf.setValue(ev.getComfortIndex());
                else if (ev.getWindSpeed() != null) wf.setValue(ev.getWindSpeed());
                else if (ev.getBeaufortScale() != null) wf.setValue(ev.getBeaufortScale());
                else if (ev.getWindDirection() != null) wf.setValue(ev.getWindDirection());
                else if (ev.getProbabilityOfPrecipitation() != null) wf.setValue(ev.getProbabilityOfPrecipitation());
                else if (ev.getWeather() != null) wf.setValue(ev.getWeather());
                else if (ev.getWeatherCode() != null) wf.setValue(ev.getWeatherCode());
                else if (ev.getWeatherDescription() != null) wf.setValue(ev.getWeatherDescription());

                weatherForecastList.add(wf);
            }

            if (weatherForecastList.isEmpty()) {
                log.warn("City {}, district {} has no weather data, skipping DB save.", city, district);
                continue;
            }

            try {
                var weatherForecastDto = new WeatherForecastDto();
                var existing = openDataRepo.findByDistrictAndCity(district, city);
                weatherForecastDto.setId(existing != null ? existing.getId() : UUID.randomUUID().toString());
                weatherForecastDto.setCity(city);
                weatherForecastDto.setDistrict(district);
                weatherForecastDto.setCreateTime(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
                weatherForecastDto.setWeatherForecast(weatherForecastList);

                openDataRepo.save(weatherForecastDto);
                log.info("Successfully updated DB for city {}, district {}.", city, district);
            } catch (Exception e) {
                log.error("Failed to update DB for city {}, district {}: {}", city, district, e.getMessage(), e);
                throw new RuntimeException("DB update failed for city " + city + ", district " + district, e);
            }
        }
    }

    // helper
    private boolean hasAnyValue(ElementValue ev) {
        return ev.getTemperature() != null ||
                ev.getDewPoint() != null ||
                ev.getRelativeHumidity() != null ||
                ev.getApparentTemperature() != null ||
                ev.getComfortIndex() != null ||
                ev.getWindSpeed() != null ||
                ev.getBeaufortScale() != null ||
                ev.getWindDirection() != null ||
                ev.getProbabilityOfPrecipitation() != null ||
                ev.getWeather() != null ||
                ev.getWeatherCode() != null ||
                ev.getWeatherDescription() != null;
    }

    private long parseTimeSafe(String str) {
        if (str == null) return 0L;
        try {
            return OffsetDateTime.parse(str).toEpochSecond();
        } catch (Exception e) {
            log.warn("Invalid time format: {}", str);
            return 0L;
        }
    }
}

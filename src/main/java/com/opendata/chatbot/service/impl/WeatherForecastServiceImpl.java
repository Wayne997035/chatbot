package com.opendata.chatbot.service.impl;

import com.opendata.chatbot.dao.WeatherForecastDto;
import com.opendata.chatbot.repository.OpenDataRepo;
import com.opendata.chatbot.service.WeatherForecastService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.*;

@Service
@Slf4j
@RequiredArgsConstructor
public class WeatherForecastServiceImpl implements WeatherForecastService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final OpenDataRepo OpenDataRepo;

    @Override
    public List<WeatherForecastDto> findByDistrict(String district) {
        try {
            // [F1-01] 讀取結果目前未被使用，本輪只確保它不會中斷回覆；是否改成 read-through 另案
            redisTemplate.opsForValue().get(district);
        } catch (Exception e) {
            log.warn("[F1-01] redis get 失敗，district={}，改走 mongo", district, e);
        }
        log.info("connect mongodb");
        return OpenDataRepo.findByDistrict(district);
    }

    @Override
    public void cacheDistrict(String district, List<WeatherForecastDto> data) {
        if (data == null || data.isEmpty()) {
            return;
        }
        try {
            // redis add
            log.info("redis cache mongodb");
            redisTemplate.opsForValue().set(district, data);
            redisTemplate.expire(district, Duration.ofHours(1));
        } catch (Exception e) {
            // [F1-01] Redis 寫入失敗只記錄，不得中斷呼叫端（回覆已經送出）
            log.warn("[F1-01] redis set 失敗，district={}", district, e);
        }
    }

    @Override
    public WeatherForecastDto findByDistrictAndCity(String district, String city) {
        log.info("location connect mongodb");
        var cityReplace = city.replace("臺", "台");
        log.info("district = {}, city = {}", district, cityReplace);
        var optionalWeatherForecastDto = OpenDataRepo.findByDistrictAndCity(district, cityReplace);
        log.info("optionalWeatherForecastDto = {}", optionalWeatherForecastDto);
        return optionalWeatherForecastDto;
    }

    @Override
    public void cacheDistrictAndCity(String district, String city, WeatherForecastDto data) {
        if (data == null) {
            return;
        }
        var cityReplace = city.replace("臺", "台");
        try {
            // [F1-01] Redis 寫入失敗只記錄，不得中斷呼叫端（回覆已經送出）
            log.info("location redis cache mongodb");
            redisTemplate.opsForValue().set(cityReplace + "_" + district, data);
            redisTemplate.expire(cityReplace + "_" + district, Duration.ofHours(1));
        } catch (Exception e) {
            log.warn("[F1-01] redis set 失敗，district={}, city={}", district, cityReplace, e);
        }
    }
}

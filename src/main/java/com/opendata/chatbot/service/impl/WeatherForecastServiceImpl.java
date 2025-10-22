package com.opendata.chatbot.service.impl;

import com.opendata.chatbot.dao.WeatherForecastDto;
import com.opendata.chatbot.repository.OpenDataRepo;
import com.opendata.chatbot.service.WeatherForecastService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.*;

@Service
@Transactional
@Slf4j
public class WeatherForecastServiceImpl implements WeatherForecastService {
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private RedisCacheManager redisCacheManager;

    @Autowired
    private OpenDataRepo OpenDataRepo;

    @Override
    public List<WeatherForecastDto> findByDistrict(String district) {
        var obj = redisTemplate.opsForValue().get(district);
//        if (null != obj) {
//            log.info("connect redis");
//            return (List<WeatherForecastDto>) obj;
//        } else {
            log.info("connect mongodb");
            var optionalList = OpenDataRepo.findByDistrict(district);
            if (!optionalList.isEmpty()) {
                // redis add
                log.info("redis cache mongodb");
                redisTemplate.opsForValue().set(district, optionalList);
                redisTemplate.expire(district, Duration.ofHours(1));
            }
            return optionalList;
//        }
    }

    @Override
    public WeatherForecastDto findByDistrictAndCity(String district, String city) {
//        var obj = redisTemplate.opsForValue().get(city + "_" + district);
//        if (null != obj) {
//            log.info("connect redis");
//            return (WeatherForecastDto) obj;
//        } else {
        log.info("location connect mongodb");
        var cityReplace = city.replace("臺", "台");
        log.info("district = {}, city = {}", district, cityReplace);
        var optionalWeatherForecastDto = OpenDataRepo.findByDistrictAndCity(district, cityReplace);
        log.info("optionalWeatherForecastDto = {}", optionalWeatherForecastDto);
        if (optionalWeatherForecastDto != null) {
            log.info("location redis cache mongodb");
            redisTemplate.opsForValue().set(cityReplace + "_" + district, optionalWeatherForecastDto);
            redisTemplate.expire(cityReplace + "_" + district, Duration.ofHours(1));
        }
        return optionalWeatherForecastDto;
//        }
    }
}

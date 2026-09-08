package com.opendata.chatbot.service;

import com.opendata.chatbot.dao.WeatherForecastDto;

import java.util.List;

public interface WeatherForecastService {
    List<WeatherForecastDto> findByDistrict(String district);
    WeatherForecastDto findByDistrictAndCity(String district, String city);

    // [F1-01] 快取寫入從 findByDistrict/findByDistrictAndCity 搬出，讓呼叫端可以在
    // LINE reply 送出之後才寫快取（AC6 順序要求），Redis 正常時 findByDistrict/
    // findByDistrictAndCity 的回傳值與修改前一致（AC5）。
    void cacheDistrict(String district, List<WeatherForecastDto> data);
    void cacheDistrictAndCity(String district, String city, WeatherForecastDto data);
}

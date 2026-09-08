package com.opendata.chatbot.service.impl;

import com.opendata.chatbot.dao.WeatherForecastDto;
import com.opendata.chatbot.repository.OpenDataRepo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

/**
 * F1-01: Redis 呼叫全面降級 —— 任何 Redis 失敗只 warn，不中斷 Mongo 查詢與快取寫入呼叫端。
 *
 * [F1-04] 這個類別能被 compileTestJava 編到、被 test 任務實際執行、產出非 0 的 tests 數 XML，
 * 本身就是 F1-04（build.gradle 兩層 exclude + useJUnitPlatform）落地的證據 —— 沒開對，
 * 這支測試連跑都不會跑。
 */
@ExtendWith(MockitoExtension.class)
class WeatherForecastServiceImplTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    @Mock
    private OpenDataRepo openDataRepo;

    private WeatherForecastServiceImpl target;

    private WeatherForecastServiceImpl newTarget() {
        return new WeatherForecastServiceImpl(redisTemplate, openDataRepo);
    }

    // [F1-01b] :24 是 text 路徑的死點：Redis GET 拋例外時 findByDistrict 仍回傳 Mongo 查到的清單。
    // AC4 revert-to-red：把 findByDistrict 的 try/catch 拿掉，本測試會因 RuntimeException 冒出而變紅。
    @Test
    void findByDistrict_redisGetThrows_returnsMongoResultAndDoesNotThrow() {
        target = newTarget();
        var dto = new WeatherForecastDto();
        dto.setCity("台北市");
        dto.setDistrict("士林區");
        var mongoList = List.of(dto);
        when(openDataRepo.findByDistrict("士林區")).thenReturn(mongoList);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("士林區")).thenThrow(new RuntimeException("redis get down"));

        var result = assertDoesNotThrow(() -> target.findByDistrict("士林區"));

        assertThat(result).isEqualTo(mongoList);
    }

    // [F1-01a] findByDistrictAndCity 的 Redis GET 已不存在（原本就沒有），本輪把快取「寫入」搬到
    // 獨立的 cacheDistrictAndCity；Redis 拋例外時 findByDistrictAndCity 仍回傳 Mongo 查到的物件，
    // 且 cacheDistrictAndCity 不拋出（只記 warn）。
    // AC4 revert-to-red：把 cacheDistrictAndCity 內的 try/catch 拿掉，本測試最後一段會因
    // RuntimeException 冒出而變紅。
    @Test
    void findByDistrictAndCity_thenCacheWriteFails_stillReturnsMongoResultAndDoesNotThrow() {
        target = newTarget();
        var dto = new WeatherForecastDto();
        dto.setCity("台北市");
        dto.setDistrict("信義區");
        when(openDataRepo.findByDistrictAndCity("信義區", "台北市")).thenReturn(dto);

        var result = target.findByDistrictAndCity("信義區", "台北市");
        assertThat(result).isSameAs(dto);

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        doThrow(new RuntimeException("redis set down")).when(valueOperations).set(any(), any());

        assertDoesNotThrow(() -> target.cacheDistrictAndCity("信義區", "台北市", dto));
    }

    // 補充：cacheDistrict 對稱驗證（district 路徑的快取寫入失敗不拋出）。
    @Test
    void cacheDistrict_redisSetThrows_doesNotThrow() {
        target = newTarget();
        var dto = new WeatherForecastDto();
        dto.setCity("台北市");
        dto.setDistrict("士林區");
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        doThrow(new RuntimeException("redis set down")).when(valueOperations).set(any(), any());

        assertDoesNotThrow(() -> target.cacheDistrict("士林區", List.of(dto)));
    }
}

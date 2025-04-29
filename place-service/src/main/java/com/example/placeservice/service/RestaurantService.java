package com.example.placeservice.service;

import com.example.placeservice.entity.Area;
import com.example.placeservice.entity.Restaurant;
import com.example.placeservice.repository.AreaRepository;
import com.example.placeservice.repository.RestaurantRepository;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RestaurantService {

    private final AreaRepository areaRepository;
    private final RestaurantRepository restaurantRepository;
    private final KakaoMapClient kakaoMapClient;

    // 특정 Area 기준으로 주변 음식점 저장
    public List<Restaurant> saveRestaurantsNearbyArea(Area area) {
        List<Restaurant> savedRestaurants = new ArrayList<>();
        int page = 1;
        boolean isEnd = false;
        double searchRadius = 1000.0; // 반경 1km
        int savedCount = 0; // 저장한 음식점 수

        while (!isEnd) {
            // ✅ Kakao API 호출 시 경도(lon), 위도(lat) 순서로 전달
            JsonNode response = kakaoMapClient.searchRestaurantsByCategory(area.getLon(), area.getLat(), page);

            if (response.has("documents")) {
                for (JsonNode doc : response.get("documents")) {
                    if (savedCount >= 15) { // 15개 저장했으면 종료
                        isEnd = true;
                        break;
                    }

                    double restaurantLon = Double.parseDouble(doc.get("x").asText());
                    double restaurantLat = Double.parseDouble(doc.get("y").asText());

                    double distance = calculateDistance(
                            area.getLat().doubleValue(),  // 위도
                            area.getLon().doubleValue(),  // 경도
                            restaurantLat,                // 위도
                            restaurantLon                 // 경도
                    );

                    // 1km 이내 음식점만 저장
                    if (distance <= searchRadius) {
                        Restaurant restaurant = new Restaurant();
                        restaurant.setArea(area);
                        restaurant.setName(doc.get("place_name").asText());
                        restaurant.setAddress(doc.get("road_address_name").asText());
                        restaurant.setLat(new BigDecimal(doc.get("y").asText())); // 위도
                        restaurant.setLon(new BigDecimal(doc.get("x").asText())); // 경도
                        restaurant.setPhone(doc.has("phone") ? doc.get("phone").asText() : null);
                        restaurant.setKakaomap_url(doc.get("place_url").asText());
                        restaurant.setCategory_code(doc.has("category_group_code") ? doc.get("category_group_code").asText() : null);
                        restaurant.setCategoryGroupName(doc.has("category_group_name") ? doc.get("category_group_name").asText() : null);
                        restaurant.setCategoryName(doc.has("category_name") ? doc.get("category_name").asText() : null);
                        restaurant.setKakao_id(doc.get("id").asText());

                        savedRestaurants.add(restaurant);
                        restaurantRepository.save(restaurant);
                        savedCount++;
                    }
                }

                if (!isEnd) {
                    isEnd = response.get("meta").get("is_end").asBoolean();
                    page++;
                }
            } else {
                isEnd = true;
            }
        }
        return savedRestaurants;
    }

    // 전체 Area 대상 음식점 저장
    @Transactional
    public void fetchAndSaveRestaurants() {
        List<Area> areas = areaRepository.findAll();
        for (Area area : areas) {
            saveRestaurantsNearbyArea(area);
        }
    }

    // Haversine 거리 계산 공식 (meter 단위)
    private double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        final int EARTH_RADIUS = 6371; // km

        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);

        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return EARTH_RADIUS * c * 1000; // meter 반환
    }
}
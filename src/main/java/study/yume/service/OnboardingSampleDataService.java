package study.yume.service;

import lombok.RequiredArgsConstructor;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import study.yume.model.DaySchedule;
import study.yume.model.Plan;
import study.yume.model.PlanDay;
import study.yume.model.Spot;
import study.yume.model.SpotGroup;
import study.yume.model.SpotPurchase;
import study.yume.model.SpotUser;
import study.yume.model.enums.PurchaseKind;
import study.yume.model.enums.PurchaseStatus;
import study.yume.model.enums.ScheduleMode;
import study.yume.model.enums.SpotType;
import study.yume.model.enums.Transportation;
import study.yume.repository.DayScheduleRepository;
import study.yume.repository.PlanDayRepository;
import study.yume.repository.PlanRepository;
import study.yume.repository.SpotGroupRepository;
import study.yume.repository.SpotPurchaseRepository;
import study.yume.repository.SpotRepository;
import study.yume.repository.SpotUserRepository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional
public class OnboardingSampleDataService {
    private static final List<PlaceSeed> PLACES = List.of(
            new PlaceSeed("kansai-airport", "ChIJ9_rNIxO5AGARiI-QjZ-ncfE", "간사이 국제공항", SpotType.STATION, "1 Senshukukokita, Izumisano, Osaka 549-0001", 34.4347, 135.2440, "간사이 여행의 도착·귀국 지점."),
            new PlaceSeed("osaka-castle", "ChIJ_TooXM3gAGARQR6hXH3QAQ8", "오사카성", SpotType.HISTORICAL_SITE, "1-1 Osakajo, Chuo Ward, Osaka 540-0002", 34.6873, 135.5262, "천수각과 성 공원을 둘러보고 오사카 시내 풍경 보기."),
            new PlaceSeed("dotonbori", "ChIJg2DcJhXnAGARCbeAHoZrPeQ", "도톤보리", SpotType.FOOD, "Dotonbori, Chuo Ward, Osaka", 34.6687, 135.5023, "저녁 식사와 글리코 사인, 강변 산책."),
            new PlaceSeed("umeda", "ChIJ7b9MGI3mAGARufc_HWvT3M0", "우메다", SpotType.SHOPPING, "Umeda, Kita Ward, Osaka", 34.7025, 135.4959, "귀국일에는 쇼핑 시간을 짧게 잡고 공항 이동 시간을 먼저 확보하기."),
            new PlaceSeed("fushimi-inari", "ChIJIW0uPRUPAWAR6eI6dRzKGns", "후시미이나리 신사", SpotType.RELIGIOUS_SITE, "68 Fukakusa Yabunouchicho, Fushimi Ward, Kyoto 612-0882", 34.9671, 135.7727, "센본도리이를 보고 산책로는 체력과 시간에 맞춰 오르기."),
            new PlaceSeed("kiyomizu-dera", "ChIJB_vchdMIAWARujTEUIZlr2I", "기요미즈데라", SpotType.RELIGIOUS_SITE, "1-294 Kiyomizu, Higashiyama Ward, Kyoto 605-0862", 34.9949, 135.7850, "경내를 둘러본 뒤 산넨자카·니넨자카 골목으로 이어가기."),
            new PlaceSeed("gion", "ChIJ8cGuvsMIAWAR4EdAvYeuwfc", "기온", SpotType.LANDMARK, "Gionmachi, Higashiyama Ward, Kyoto", 35.0037, 135.7788, "저녁 산책과 식사. 주택가와 사유지 촬영 안내를 지키기."),
            new PlaceSeed("nara-park", "ChIJYWCMvZY5AWARVnREV_OsbPk", "나라공원", SpotType.PARK, "Zoshicho, Nara 630-8211", 34.6851, 135.8430, "공원 산책과 사슴 구경. 사슴에게는 사슴 전용 과자만 주기."),
            new PlaceSeed("todaiji", "ChIJ3XYIepA5AWARjzzVnT-skPg", "도다이지", SpotType.RELIGIOUS_SITE, "406-1 Zoshicho, Nara 630-8587", 34.6890, 135.8398, "대불전과 난다이몬을 둘러보기."),
            new PlaceSeed("arashiyama-bamboo", "ChIJrYtcv-urAWAR3XzWvXv8n_s", "아라시야마 대나무숲", SpotType.NATURE, "Sagaogurayama Tabuchiyamacho, Ukyo Ward, Kyoto", 35.0171, 135.6713, "사람이 몰리기 전에 오전 산책으로 들르기."),
            new PlaceSeed("tenryu-ji", "ChIJk54PuAGqAWARwEgz_9o-nM0", "텐류지", SpotType.RELIGIOUS_SITE, "68 Saga-tenryuji-susukinobabacho, Ukyo Ward, Kyoto 616-8385", 35.0159, 135.6737, "정원과 아라시야마 풍경을 함께 보기."),
            new PlaceSeed("togetsukyo", "ChIJd2mRAFMHAWARPSiWJOj4FOw", "도게츠교", SpotType.LANDMARK, "Nakanoshima, Saga, Ukyo Ward, Kyoto", 35.0094, 135.6676, "다리와 강변을 천천히 걷고 주변에서 쉬기."),
            new PlaceSeed("ginkaku-ji", "ChIJ4W9CCwUJAWARyauI6BzKiiU", "은각사", SpotType.RELIGIOUS_SITE, "2 Ginkakujicho, Sakyo Ward, Kyoto 606-8402", 35.0270, 135.7982, "정원과 전망을 둘러본 뒤 철학의 길로 이동하기."),
            new PlaceSeed("philosophers-path", "ChIJV_CUrggJAWAR9IBSpzHzepE", "철학의 길", SpotType.PARK, "Sakyo Ward, Kyoto", 35.0231, 135.7918, "운하를 따라 은각사와 난젠지 사이를 산책하기."),
            new PlaceSeed("nanzen-ji", "ChIJ_fuXcyEJAWARTQDnx6Q5szg", "난젠지", SpotType.RELIGIOUS_SITE, "86 Nanzenji Fukuchicho, Sakyo Ward, Kyoto 606-8435", 35.0116, 135.7948, "수로각과 경내를 둘러보고 주변에서 점심 먹기."),

            // 일정에 등장하지 않는 교토·나라 보관함 장소 15곳
            new PlaceSeed("kinkaku-ji", "ChIJvUbrwCCoAWARX2QiHCsn5A4", "킨카쿠지(금각사)", SpotType.RELIGIOUS_SITE, "1 Kinkakujicho, Kita Ward, Kyoto 603-8361", 35.0394, 135.7292, "연못에 비치는 금각과 정원을 함께 둘러보기."),
            new PlaceSeed("ryoan-ji", "ChIJp7ocMCqoAWARQoXXRj4Xq-E", "료안지", SpotType.RELIGIOUS_SITE, "13 Ryoanji Goryonoshitacho, Ukyo Ward, Kyoto 616-8001", 35.0346, 135.7183, "돌정원을 보고 연못 주변까지 천천히 산책하기."),
            new PlaceSeed("nijo-castle", "ChIJC5srCtQHAWARLy9qkFmHaxA", "니조성", SpotType.HISTORICAL_SITE, "541 Nijojocho, Nakagyo Ward, Kyoto 604-8301", 35.0142, 135.7481, "니노마루 궁전과 정원까지 둘러보기."),
            new PlaceSeed("nishiki-market", "ChIJT8uMzZwIAWARnGzsARCjnrY", "니시키 시장", SpotType.SHOPPING, "Nishikikoji Teramachi-Takakura, Nakagyo Ward, Kyoto", 35.0050, 135.7649, "교토 음식과 간식 구경. 산 음식은 가게 앞 지정 장소에서 먹기."),
            new PlaceSeed("to-ji", "ChIJTar7hQQGAWAREHkXsNkt7tM", "도지", SpotType.RELIGIOUS_SITE, "1 Kujocho, Minami Ward, Kyoto 601-8473", 34.9806, 135.7477, "오층탑과 금당·강당 둘러보기. 매월 21일에는 고보시장도 확인하기."),
            new PlaceSeed("sanjusangen-do", "ChIJs4Cbj8oIAWARiBZl2-sBK6o", "산주산겐도", SpotType.RELIGIOUS_SITE, "657 Sanjusangendomawari, Higashiyama Ward, Kyoto 605-0941", 34.9875, 135.7719, "긴 본당 안의 관음상과 조각상 감상하기."),
            new PlaceSeed("kyoto-national-museum", "ChIJt1kBRcoIAWAR00msugECEYw", "교토 국립박물관", SpotType.MUSEUM, "527 Chayacho, Higashiyama Ward, Kyoto 605-0931", 34.9908, 135.7725, "관심 있는 전시가 있는지 확인하고 방문하기."),
            new PlaceSeed("kyoto-railway-museum", "ChIJQ8KtRhcGAWAR7XnOmoj6n4k", "교토 철도박물관", SpotType.MUSEUM, "Kankijicho, Shimogyo Ward, Kyoto 600-8835", 34.9864, 135.7469, "철도 차량과 전시를 둘러보며 시간을 넉넉히 잡기."),
            new PlaceSeed("gekkeikan-museum", "ChIJLZRyoJIPAWAR0xnG_CrMzn4", "월계관 사케박물관", SpotType.MUSEUM, "247 Minamihama-cho, Fushimi Ward, Kyoto 612-8043", 34.9324, 135.7618, "사케 양조 역사와 도구 살펴보기. 시음 가능 여부는 방문 전에 확인하기."),
            new PlaceSeed("byodoin", "ChIJqyva4AwRAWARpAf6j5MUhGA", "우지 뵤도인", SpotType.RELIGIOUS_SITE, "116 Uji Renge, Uji, Kyoto 611-0021", 34.8893, 135.8077, "봉황당과 정원, 박물관 둘러보기."),
            new PlaceSeed("uji-tea-street", "ChIJ4-8dgQsRAWARGfPkxvbIFKI", "우지 뵤도인 앞 찻집 거리", SpotType.SHOPPING, "Uji Renge, Uji, Kyoto", 34.8902, 135.8071, "말차와 차 디저트를 맛보고 기념품 구경하기."),
            new PlaceSeed("shimogamo-shrine", "ChIJf438RkAIAWARzM3lfzI7uAM", "시모가모 신사", SpotType.RELIGIOUS_SITE, "59 Shimogamo Izumigawacho, Sakyo Ward, Kyoto 606-0807", 35.0394, 135.7729, "신사 참배 후 타다스노모리 숲길 걷기."),
            new PlaceSeed("kofuku-ji", "ChIJs-w9sog5AWARk0WDN0cPgxE", "고후쿠지", SpotType.RELIGIOUS_SITE, "48 Noboriojicho, Nara 630-8213", 34.6830, 135.8328, "중금당과 국보관 중 관심 있는 곳을 골라 둘러보기."),
            new PlaceSeed("isuien", "ChIJc31a-Y85AWARo70kM5pXEVo", "이스이엔 정원", SpotType.PARK, "74 Suimoncho, Nara 630-8208", 34.6850, 135.8325, "정원 풍경을 보며 여유 있게 산책하기."),
            new PlaceSeed("naramachi", "ChIJC4EZk5E5AWAR_R-Lp860alk", "나라마치", SpotType.HISTORICAL_SITE, "Naramachi, Nara", 34.6766, 135.8301, "옛 거리와 마치야, 작은 상점 구경하기.")
    );

    private final PlanRepository planRepository;
    private final PlanDayRepository planDayRepository;
    private final DayScheduleRepository dayScheduleRepository;
    private final SpotRepository spotRepository;
    private final SpotGroupRepository spotGroupRepository;
    private final SpotPurchaseRepository spotPurchaseRepository;
    private final SpotUserRepository spotUserRepository;

    private final GeometryFactory geometryFactory = new GeometryFactory(new PrecisionModel(), 4326);

    public void createForUser(Long userId, LocalDate signupDate) {
        Map<String, SpotUser> userSpots = new HashMap<>();
        for (PlaceSeed place : PLACES) {
            userSpots.put(place.key(), createOrGetUserSpot(userId, place));
        }
        createSampleGroups(userId, userSpots);
        createSamplePurchases(userId, signupDate, userSpots);

        Plan sampleTrip = new Plan();
        sampleTrip.setUserId(userId);
        sampleTrip.setPlanName("[예시] 오사카·교토·나라 3일 여행");
        sampleTrip.setPlanStartDate(signupDate.minusDays(1));
        sampleTrip.setPlanEndDate(signupDate.plusDays(1));
        sampleTrip.setPlanDays(3);
        sampleTrip.setPlanMemo("첫날은 오사카성 보고 도톤보리에서 저녁. 둘째 날은 후시미이나리부터 기요미즈데라, 기온까지 둘러본다. 마지막 날은 나라공원과 도다이지를 보고 우메다에 잠깐 들른 뒤 공항으로 이동. 오전 도착, 저녁 출국 항공편 기준.");
        sampleTrip = planRepository.save(sampleTrip);

        createPlanDay(userId, sampleTrip, 1, "[예시] 1일차 · 오사카", "간사이공항에서 오사카로 이동해 짐부터 맡기기. 오사카성 둘러보고 저녁에는 도톤보리에서 타코야키 먹기.", List.of(
                Stop.place("kansai-airport", LocalTime.of(8, 30), 90, 0, null, null),
                Stop.note("오사카 이동·짐 맡기기", SpotType.OTHER, LocalTime.of(11, 0), 60, 60, Transportation.TRAIN, "숙소에 짐을 맡기고 오사카성으로 이동."),
                Stop.place("osaka-castle", LocalTime.of(13, 0), 120, 60, Transportation.TRAIN, null),
                Stop.note("점심", SpotType.FOOD, LocalTime.of(15, 30), 60, 30, Transportation.WALK, "성 주변에서 점심 먹기."),
                Stop.place("dotonbori", LocalTime.of(17, 15), 150, 45, Transportation.TRAIN, "저녁 식사와 강변 산책.")
        ), userSpots);

        createPlanDay(userId, sampleTrip, 2, "[예시] 2일차 · 교토", "후시미이나리는 오전에 먼저 보고 기요미즈데라 쪽으로 이동. 히가시야마 골목을 걷다가 기온에서 저녁 먹기.", List.of(
                Stop.note("오사카에서 교토로 이동", SpotType.STATION, LocalTime.of(7, 0), 60, 0, Transportation.TRAIN, "아침에 오사카를 출발해 후시미이나리로 이동."),
                Stop.place("fushimi-inari", LocalTime.of(8, 30), 105, 30, Transportation.TRAIN, null),
                Stop.note("교토에서 점심", SpotType.FOOD, LocalTime.of(11, 15), 60, 60, Transportation.TRAIN, "기요미즈데라로 이동하기 전에 점심."),
                Stop.place("kiyomizu-dera", LocalTime.of(12, 45), 120, 30, Transportation.BUS, "산넨자카·니넨자카 골목으로 이어가기."),
                Stop.place("gion", LocalTime.of(15, 30), 120, 45, Transportation.WALK, "기온을 걷고 저녁 식사 후 오사카로 돌아가기.")
        ), userSpots);

        createPlanDay(userId, sampleTrip, 3, "[예시] 3일차 · 나라·우메다에서 귀국", "체크아웃 후 숙소에 짐 맡기고 나라로 이동. 나라공원과 도다이지를 보고 오사카로 돌아와 짐을 찾은 뒤 우메다에서 쇼핑 조금. 비행기 시간이 빠르면 우메다를 줄이고 공항으로 바로 가기.", List.of(
                Stop.note("체크아웃·짐 맡기기", SpotType.OTHER, LocalTime.of(7, 0), 30, 0, null, "가벼운 짐만 챙겨 나라로 출발."),
                Stop.place("nara-park", LocalTime.of(8, 30), 90, 60, Transportation.TRAIN, null),
                Stop.place("todaiji", LocalTime.of(10, 15), 120, 15, Transportation.WALK, "대불전과 난다이몬 둘러보기."),
                Stop.note("나라에서 점심", SpotType.FOOD, LocalTime.of(12, 30), 60, 15, Transportation.WALK, "점심 후 우메다로 이동."),
                Stop.place("umeda", LocalTime.of(14, 30), 90, 60, Transportation.TRAIN, "숙소에서 짐을 찾아 쇼핑 시간을 짧게 잡고 공항 이동 시간을 먼저 확보."),
                Stop.place("kansai-airport", LocalTime.of(17, 15), 120, 75, Transportation.TRAIN, "체크인과 출국 수속 후 저녁 비행기로 귀국.")
        ), userSpots);

        createIndependentDay(userId, "[예시] 아라시야마 하루", "아침에 아라시야마로 가서 대나무숲부터 걷기. 텐류지 정원 보고 근처에서 점심 먹은 다음, 도게츠교까지 천천히 둘러보기.", List.of(
                Stop.place("arashiyama-bamboo", LocalTime.of(9, 0), 90, 0, null, null),
                Stop.place("tenryu-ji", LocalTime.of(10, 45), 90, 15, Transportation.WALK, "정원과 아라시야마 풍경 보기."),
                Stop.note("아라시야마에서 점심", SpotType.FOOD, LocalTime.of(12, 45), 60, 30, Transportation.WALK, "역 주변이나 강변에서 점심."),
                Stop.place("togetsukyo", LocalTime.of(14, 0), 60, 15, Transportation.WALK, "다리와 강변 산책 후 오사카로 돌아가기.")
        ), userSpots);

        createIndependentDay(userId, "[예시] 교토 동서쪽 하루 코스", "은각사에서 시작해 철학의 길 따라 난젠지까지 걷기. 난젠지 근처에서 점심 먹고 오후에는 아라시야마로 이동해 대나무숲과 도게츠교 보기. 이동이 길어질 수 있으니 아라시야마에서는 둘러볼 곳을 추려두기.", List.of(
                Stop.place("ginkaku-ji", LocalTime.of(8, 30), 60, 0, null, null),
                Stop.place("philosophers-path", LocalTime.of(9, 45), 60, 15, Transportation.WALK, "운하를 따라 난젠지 방향으로 걷기."),
                Stop.place("nanzen-ji", LocalTime.of(11, 0), 75, 15, Transportation.WALK, "수로각과 경내를 둘러보기."),
                Stop.note("난젠지 근처에서 점심", SpotType.FOOD, LocalTime.of(12, 30), 60, 15, Transportation.WALK, "점심 후 아라시야마로 이동."),
                Stop.place("arashiyama-bamboo", LocalTime.of(14, 0), 75, 60, Transportation.TRAIN, "도착 시간에 맞춰 대나무숲 산책."),
                Stop.place("togetsukyo", LocalTime.of(15, 30), 60, 15, Transportation.WALK, "시간이 남으면 강변에서 쉬었다가 오사카로 돌아가기.")
        ), userSpots);
    }

    private void createSampleGroups(Long userId, Map<String, SpotUser> userSpots) {
        SpotGroup kyotoWalk = createSampleGroup(userId, "[예시] 교토 동쪽 산책");
        addSpotsToGroup(kyotoWalk, userSpots, List.of(
                "ginkaku-ji", "philosophers-path", "nanzen-ji", "kiyomizu-dera"
        ));

        SpotGroup kansaiHighlights = createSampleGroup(userId, "[예시] 간사이 대표 여행지");
        addSpotsToGroup(kansaiHighlights, userSpots, List.of(
                "osaka-castle", "dotonbori", "fushimi-inari", "nara-park", "todaiji"
        ));
    }

    private SpotGroup createSampleGroup(Long userId, String name) {
        SpotGroup group = new SpotGroup();
        group.setUserId(userId);
        group.setGroupName(name);
        return spotGroupRepository.save(group);
    }

    private void addSpotsToGroup(SpotGroup group, Map<String, SpotUser> userSpots, List<String> placeKeys) {
        for (String placeKey : placeKeys) {
            SpotUser spotUser = userSpots.get(placeKey);
            if (spotUser == null) continue;
            spotUser.getSpotGroup().add(group);
            spotUserRepository.save(spotUser);
        }
    }

    private void createSamplePurchases(Long userId, LocalDate signupDate, Map<String, SpotUser> userSpots) {
        createSamplePurchase(userId, userSpots.get("fushimi-inari"), PurchaseKind.GOSHUIN,
                "고슈인", "[예시] 후시미이나리 고슈인", PurchaseStatus.WANT, null,
                "참배 후 수여소 운영시간을 확인해요.");
        createSamplePurchase(userId, userSpots.get("nara-park"), PurchaseKind.SOUVENIR,
                "마그넷", "[예시] 나라 사슴 마그넷", PurchaseStatus.ACQUIRED, signupDate,
                "구매 완료 예시 기록이에요.");
        createSamplePurchase(userId, userSpots.get("uji-tea-street"), PurchaseKind.FOOD_ITEM,
                "차·간식", "[예시] 우지 말차 과자", PurchaseStatus.WANT, null,
                "여행 중 살 간식 후보예요.");
    }

    private void createSamplePurchase(Long userId, SpotUser spotUser, PurchaseKind kind, String category,
                                      String itemName, PurchaseStatus status, LocalDate acquiredDate, String note) {
        SpotPurchase purchase = new SpotPurchase();
        purchase.setUserId(userId);
        purchase.setSpotUser(spotUser);
        purchase.setKind(kind);
        purchase.setCategory(category);
        purchase.setItemName(itemName);
        purchase.setStatus(status);
        purchase.setQuantity(1);
        purchase.setCurrency("JPY");
        purchase.setAcquiredDate(acquiredDate);
        purchase.setNote(note);
        spotPurchaseRepository.save(purchase);
    }

    private SpotUser createOrGetUserSpot(Long userId, PlaceSeed seed) {
        String seedPlaceId = seed.googlePlaceId();
        Spot spot = spotRepository.findByPlaceId(seedPlaceId).orElseGet(() -> {
            Spot created = new Spot();
            created.setPlaceId(seedPlaceId);
            created.setSpotName(seed.name());
            created.setAddress(seed.address());
            created.setShortAddress(seed.address().substring(seed.address().lastIndexOf(',') + 1).trim());
            created.setGoogleMapUrl("https://www.google.com/maps/search/?api=1&query=" + seed.lat() + "," + seed.lng());
            created.setLocation(geometryFactory.createPoint(new Coordinate(seed.lng(), seed.lat())));
            created.setMetadata(Map.of("source", "signup-sample"));
            return spotRepository.save(created);
        });

        return spotUserRepository.findByUserIdAndSpotId(userId, spot.getId()).orElseGet(() -> {
            SpotUser savedPlace = new SpotUser();
            savedPlace.setUserId(userId);
            savedPlace.setSpot(spot);
            savedPlace.setSpotType(seed.type());
            savedPlace.setCustomName(seed.name());
            savedPlace.setIsVisit(false);
            savedPlace.setDescription(seed.note());
            savedPlace.setMetadata(new HashMap<>());
            return spotUserRepository.save(savedPlace);
        });
    }

    private void createPlanDay(Long userId, Plan plan, int order, String name, String memo,
                               List<Stop> stops, Map<String, SpotUser> userSpots) {
        PlanDay day = new PlanDay();
        day.setUserId(userId);
        day.setPlan(plan);
        day.setDayOrder(order);
        day.setDayName(name);
        day.setMemo(memo);
        day.setScheduleMode(ScheduleMode.DETAILED);
        day = planDayRepository.save(day);
        createSchedules(userId, day, stops, userSpots);
    }

    private void createIndependentDay(Long userId, String name, String memo,
                                      List<Stop> stops, Map<String, SpotUser> userSpots) {
        PlanDay day = new PlanDay();
        day.setUserId(userId);
        day.setPlan(null);
        day.setDayOrder(1);
        day.setDayName(name);
        day.setMemo(memo);
        day.setScheduleMode(ScheduleMode.DETAILED);
        day = planDayRepository.save(day);
        createSchedules(userId, day, stops, userSpots);
    }

    private void createSchedules(Long userId, PlanDay day, List<Stop> stops, Map<String, SpotUser> userSpots) {
        for (int order = 0; order < stops.size(); order++) {
            Stop seed = stops.get(order);
            SpotUser spotUser = seed.placeKey() == null ? null : userSpots.get(seed.placeKey());
            DaySchedule schedule = new DaySchedule();
            schedule.setUserId(userId);
            schedule.setPlanDay(day);
            schedule.setScheduleOrder(order);
            schedule.setSpotUser(spotUser);
            schedule.setSpotNameSnapshot(spotUser == null ? seed.name() : spotUser.getCustomName());
            schedule.setSpotTypeSnapshot(spotUser == null ? seed.type() : spotUser.getSpotType());
            if (spotUser != null && spotUser.getSpot().getLocation() != null) {
                schedule.setSpotLocationSnapshot((Point) spotUser.getSpot().getLocation().copy());
            }
            schedule.setIsChecked(false);
            schedule.setIsSkipped(false);
            schedule.setStartTime(seed.startTime());
            schedule.setFixedStartTime(false);
            schedule.setDuration(seed.duration());
            schedule.setEndTime(seed.startTime().plusMinutes(seed.duration()));
            schedule.setMovingDuration(seed.movingDuration());
            schedule.setExtraDuration(0);
            schedule.setExtraMovingDuration(0);
            schedule.setTransportation(seed.transportation());
            schedule.setMemo(seed.memo());
            dayScheduleRepository.save(schedule);
        }
    }

    private record PlaceSeed(String key, String googlePlaceId, String name, SpotType type, String address,
                             double lat, double lng, String note) { }

    private record Stop(String placeKey, String name, SpotType type, LocalTime startTime,
                        int duration, int movingDuration, Transportation transportation, String memo) {
        private static Stop place(String placeKey, LocalTime start, int duration, int moving,
                                  Transportation transportation, String memo) {
            return new Stop(placeKey, null, null, start, duration, moving, transportation, memo);
        }

        private static Stop note(String name, SpotType type, LocalTime start, int duration, int moving,
                                 Transportation transportation, String memo) {
            return new Stop(null, name, type, start, duration, moving, transportation, memo);
        }
    }
}

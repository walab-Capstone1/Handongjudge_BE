package com.project.handongjudge.community.util;

import java.security.SecureRandom;

/**
 * 커뮤니티 익명 질문 표시용: 형용사 + 명사 조합 (클래스크래프트 스타일)
 */
public final class CommunityDisplayNameGenerator {

    private static final SecureRandom RANDOM = new SecureRandom();

    private static final String[] ADJECTIVES = {
            "멋진", "용감한", "똑똑한", "귀여운", "신나는", "빠른", "조용한", "친절한", "활발한", "착한",
            "열정적인", "차분한", "밝은", "당당한", "영리한", "맑은", "든든한", "상냥한", "유쾌한", "씩씩한",
            "총명한", "다정한", "명랑한", "기운찬", "포근한", "산뜻한", "반짝이는", "늠름한", "싱그러운",
            "재빠른", "느긋한", "기특한", "사랑스러운", "호탕한", "정직한", "온화한", "쾌활한", "소심한", "대담한"
    };

    private static final String[] NOUNS = {
            "잠자리", "펭귄", "호랑이", "사자", "고양이", "강아지", "독수리", "참새", "다람쥐", "햄스터",
            "거북이", "두더지", "너구리", "수달", "늑대", "여우", "곰", "판다", "코알라", "토끼",
            "돌고래", "고래", "문어", "해파리", "가오리", "부엉이", "까치", "까마귀", "두루미", "공작",
            "나무늘보", "카멜레온", "이구아나", "앵무새", "플라밍고", "기린", "얼룩말", "하마", "코뿔소", "수리부엉이"
    };

    private CommunityDisplayNameGenerator() {
    }

    /**
     * @return "형용사 명사" 형태, DB 컬럼 길이(50) 이내
     */
    public static String randomDisplayName() {
        String adj = ADJECTIVES[RANDOM.nextInt(ADJECTIVES.length)];
        String noun = NOUNS[RANDOM.nextInt(NOUNS.length)];
        String combined = adj + " " + noun;
        return combined.length() > 50 ? combined.substring(0, 50) : combined;
    }

    /**
     * 충돌 시 붙일 짧은 접미사 (숫자만)
     */
    public static String numericSuffix() {
        return String.valueOf(1000 + RANDOM.nextInt(9000));
    }
}

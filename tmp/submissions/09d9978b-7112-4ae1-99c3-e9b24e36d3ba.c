#include <stdio.h>

int main() {
    int size, num;
    int pos = 0, neg = 0, zero = 0;
    int max, min, sum = 0;
    double average;
    
    // 첫 번째 수의 개수 입력
    scanf("%d", &size);
    
    // 첫 번째 수를 입력받아 초기값 설정
    scanf("%d", &num);
    max = min = num;
    sum = num;
    
    // 첫 번째 수 분류
    if (num > 0) {
        pos++;
    } else if (num < 0) {
        neg++;
    } else {
        zero++;
    }
    
    // 나머지 수들을 입력받으면서 처리
    for (int i = 1; i < size; i++) {
        scanf("%d", &num);
        
        // 양수, 음수, 0 개수 계산
        if (num > 0) {
            pos++;
        } else if (num < 0) {
            neg++;
        } else {
            zero++;
        }
        
        // 최댓값, 최솟값 업데이트
        if (num > max) {
            max = num;
        }
        if (num < min) {
            min = num;
        }
        
        // 합계 계산
        sum += num;
    }
    
    // 평균 계산
    average = (double)sum / size;
    
    // 결과 출력
    printf("Positive: %d\n", pos);
    printf("Negative: %d\n", neg);
    printf("Zero: %d\n", zero);
    printf("Max: %d\n", max);
    printf("Min: %d\n", min);
    printf("Sum: %d\n", sum);
    printf("Average: %.1f\n", average);
    
    return 0;
}